package iuh.fit.workflowservice.infrastructure.camunda;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientHttpException;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.Variable;
import iuh.fit.workflowservice.application.dto.cancellation.CancellationState;
import iuh.fit.workflowservice.application.dto.cancellation.StartCancellationCommand;
import iuh.fit.workflowservice.application.port.CancellationProcessPort;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CamundaCancellationProcessAdapter implements CancellationProcessPort {
    static final String PROCESS_ID = "Process_BookingCancellationRefund";
    static final String DECIDED_MESSAGE = "BookingCancellationDecided";
    static final String REFUND_STATUS_MESSAGE = "RefundStatusChanged";
    static final String WAIT_REFUND_ELEMENT = "Event_RefundStatusChanged";
    /** Sự kiện khoản hoàn tới trước khi process kịp tới bước chờ thì được giữ lại tối đa chừng này. */
    private static final Duration REFUND_MESSAGE_TTL = Duration.ofDays(1);
    private static final int PAGE_SIZE = 100;

    private final CamundaClient camundaClient;
    private final ObjectMapper objectMapper;

    @Override
    public long start(StartCancellationCommand command) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("cancellationId", command.cancellationId().toString());
        variables.put("bookingId", command.bookingId().toString());
        variables.put("reviewMode", command.reviewMode());
        variables.put("refundAmount", command.refundAmount());
        variables.put("playStartAt", command.playStartAt());
        variables.put("decisionNote", command.decisionNote());
        variables.put("processedBy", null);
        variables.put("status", command.resumeStatus() == null ? "PENDING" : command.resumeStatus());

        CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3 create = camundaClient.newCreateInstanceCommand()
                .bpmnProcessId(PROCESS_ID)
                .latestVersion();
        // Yêu cầu hủy cũ đang dở phần hoàn tiền: vào thẳng bước chờ payment-service, mang theo trạng thái khoản hoàn.
        if (command.resumeStatus() != null && command.resumeStatus().startsWith("REFUND_")) {
            variables.put("refundId", command.refundId().toString());
            variables.put("refundStatus", command.resumeStatus().substring("REFUND_".length()));
            create = create.startBeforeElement(WAIT_REFUND_ELEMENT);
        }
        return create.variables(variables).send().join().getProcessInstanceKey();
    }

    @Override
    public boolean decide(UUID cancellationId, boolean approved, UUID processedBy, String decisionNote) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("approved", approved);
        variables.put("processedBy", processedBy.toString());
        variables.put("decisionNote", decisionNote);
        try {
            camundaClient.newCorrelateMessageCommand()
                    .messageName(DECIDED_MESSAGE)
                    .correlationKey(cancellationId.toString())
                    .variables(variables)
                    .send()
                    .join();
            return true;
        } catch (ClientHttpException exception) {
            if (exception.code() == 404) return false;
            throw exception;
        } catch (ClientStatusException exception) {
            if (exception.getStatusCode() == Status.Code.NOT_FOUND) return false;
            throw exception;
        }
    }

    @Override
    public void publishRefundStatus(UUID refundId, String refundStatus, String failureReason, String messageId) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("refundStatus", refundStatus);
        variables.put("refundFailureReason", failureReason);
        try {
            camundaClient.newPublishMessageCommand()
                    .messageName(REFUND_STATUS_MESSAGE)
                    .correlationKey(refundId.toString())
                    .messageId(messageId)
                    .timeToLive(REFUND_MESSAGE_TTL)
                    .variables(variables)
                    .send()
                    .join();
        } catch (ClientStatusException exception) {
            // Kafka giao lại cùng event: message đã có (cùng messageId) thì coi như xong.
            if (exception.getStatusCode() != Status.Code.ALREADY_EXISTS) throw exception;
        } catch (ClientHttpException exception) {
            if (exception.code() != 409) throw exception;
        }
    }

    @Override
    public List<CancellationState> activeStates() {
        List<CancellationState> states = new ArrayList<>();
        String cursor = null;
        while (true) {
            String after = cursor;
            var page = camundaClient.newProcessInstanceSearchRequest()
                    .filter(filter -> filter.processDefinitionId(PROCESS_ID).state(ProcessInstanceState.ACTIVE))
                    .page(pagination -> {
                        pagination.limit(PAGE_SIZE);
                        if (after != null) pagination.after(after);
                    })
                    .send()
                    .join();
            for (ProcessInstance instance : page.items()) {
                CancellationState state = stateOf(instance.getProcessInstanceKey());
                if (state != null) states.add(state);
            }
            if (page.items().size() < PAGE_SIZE || page.page().endCursor() == null) return states;
            cursor = page.page().endCursor();
        }
    }

    /** Biến ở scope process (không lấy biến cục bộ của task đang chạy). Chưa ghi trạng thái lần nào thì bỏ qua. */
    private CancellationState stateOf(long processInstanceKey) {
        Map<String, JsonNode> variables = new HashMap<>();
        camundaClient.newVariableSearchRequest()
                .filter(filter -> filter.processInstanceKey(processInstanceKey).scopeKey(processInstanceKey))
                .page(pagination -> pagination.limit(PAGE_SIZE))
                .send()
                .join()
                .items()
                .forEach(variable -> variables.put(variable.getName(), json(variable)));
        JsonNode version = variables.get("statusVersion");
        if (version == null || !version.canConvertToLong()) return null;
        return new CancellationState(
                UUID.fromString(text(variables, "cancellationId")),
                UUID.fromString(text(variables, "bookingId")),
                text(variables, "status"),
                version.asLong(),
                uuidOrNull(text(variables, "processedBy")),
                text(variables, "decisionNote"),
                text(variables, "refundFailureReason")
        );
    }

    private JsonNode json(Variable variable) {
        try {
            return objectMapper.readTree(variable.getValue());
        } catch (Exception exception) {
            throw new IllegalStateException("Biến " + variable.getName() + " không phải JSON hợp lệ", exception);
        }
    }

    private static String text(Map<String, JsonNode> variables, String name) {
        JsonNode value = variables.get(name);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static UUID uuidOrNull(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }
}
