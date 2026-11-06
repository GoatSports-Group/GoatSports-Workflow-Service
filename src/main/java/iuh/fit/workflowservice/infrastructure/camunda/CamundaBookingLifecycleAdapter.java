package iuh.fit.workflowservice.infrastructure.camunda;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientHttpException;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.client.api.command.CreateProcessInstanceCommandStep1;
import io.grpc.Status;
import iuh.fit.workflowservice.application.dto.lifecycle.StartBookingLifecycleCommand;
import iuh.fit.workflowservice.application.port.BookingLifecycleProcessPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CamundaBookingLifecycleAdapter implements BookingLifecycleProcessPort {
    static final String PROCESS_ID = "Process_BookingLifecycle";
    /**
     * venue-service báo "đã thu phần còn lại" rồi "đã nhận sân" liền nhau: message sau có thể tới khi process chưa kịp
     * sang bước chờ nó, nên được giữ lại một lúc thay vì mất.
     */
    private static final Duration MESSAGE_TTL = Duration.ofMinutes(10);

    private final CamundaClient camundaClient;

    @Override
    public long start(StartBookingLifecycleCommand command) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("bookingId", command.bookingId().toString());
        variables.put("holdExpiresAt", command.holdExpiresAt());
        variables.put("playEndAt", command.playEndAt());
        variables.put("bookingStatus", command.status());

        CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3 create = camundaClient.newCreateInstanceCommand()
                .bpmnProcessId(PROCESS_ID)
                .latestVersion();
        String startElement = startElement(command);
        if (startElement != null) create = create.startBeforeElement(startElement);
        return create.variables(variables).send().join().getProcessInstanceKey();
    }

    @Override
    public void publish(UUID bookingId, String messageName, String messageId) {
        try {
            camundaClient.newPublishMessageCommand()
                    .messageName(messageName)
                    .correlationKey(bookingId.toString())
                    .messageId(messageId)
                    .timeToLive(MESSAGE_TTL)
                    .send()
                    .join();
        } catch (ClientStatusException exception) {
            // venue-service gửi lại cùng message (thử lại, nhiều nơi cùng báo): đã có thì coi như xong.
            if (exception.getStatusCode() != Status.Code.ALREADY_EXISTS) throw exception;
        } catch (ClientHttpException exception) {
            if (exception.code() != 409) throw exception;
        }
    }

    /** Đơn đã qua bước chờ cọc (khách tại quầy, đơn cũ được nhận nuôi): vào thẳng bước đang dở. */
    static String startElement(StartBookingLifecycleCommand command) {
        return switch (command.status()) {
            case "PENDING_PAYMENT" -> null;
            case "CONFIRMED" -> command.remainingPaid() ? "Gateway_WaitCheckIn" : "Gateway_WaitArrival";
            case "CHECKED_IN" -> "Event_PlayEnded";
            default -> throw new IllegalArgumentException("Đơn " + command.bookingId() + " ở trạng thái "
                    + command.status() + " không còn vòng đời để theo dõi");
        };
    }
}
