package iuh.fit.workflowservice.infrastructure.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.exception.CamundaError;
import iuh.fit.workflowservice.application.dto.cancellation.CancellationState;
import iuh.fit.workflowservice.application.dto.cancellation.CreatedRefund;
import iuh.fit.workflowservice.application.exception.CancellationMissingException;
import iuh.fit.workflowservice.application.port.VenueCancellationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Worker của process "Hủy đặt sân và hoàn tiền". Lỗi tạm (venue-service/payment-service chưa phản hồi) để Camunda
 * thử lại theo retries của task, hết lượt thì thành incident trong Operate. Venue-service không còn yêu cầu hủy thì
 * ném lỗi BPMN để process kết thúc ở nhánh "Yêu cầu hủy không còn".
 */
@Component
@RequiredArgsConstructor
public class BookingCancellationJobWorker {
    static final String CANCELLATION_MISSING = "CANCELLATION_NOT_FOUND";
    private static final long RETRY_BACKOFF_MS = 5_000;

    private final VenueCancellationPort venue;

    /** Ghi trạng thái do process quyết định sang venue-service; version = job key (tăng dần trong một process). */
    @JobWorker(type = "booking-cancellation.apply-state", retryBackoff = RETRY_BACKOFF_MS)
    public Map<String, Object> applyState(ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        CancellationState state = new CancellationState(
                uuid(variables, "cancellationId"),
                uuid(variables, "bookingId"),
                text(variables, "status"),
                job.getKey(),
                uuid(variables, "processedBy"),
                text(variables, "decisionNote"),
                text(variables, "refundFailureReason")
        );
        run(() -> venue.applyState(state));
        return Map.of("status", state.status(), "statusVersion", state.version());
    }

    @JobWorker(type = "booking-cancellation.create-refund", retryBackoff = RETRY_BACKOFF_MS)
    public Map<String, Object> createRefund(ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        CreatedRefund refund = guard(() -> venue.createRefund(
                uuid(variables, "bookingId"), uuid(variables, "cancellationId"), uuid(variables, "processedBy")));
        Map<String, Object> result = new HashMap<>();
        result.put("refundId", refund.refundId().toString());
        result.put("refundStatus", refund.status());
        result.put("refundFailureReason", refund.failureReason());
        return result;
    }

    @JobWorker(type = "booking-cancellation.remind-owner", retryBackoff = RETRY_BACKOFF_MS)
    public void remindOwner(ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        run(() -> venue.remindOwner(uuid(variables, "bookingId"), uuid(variables, "cancellationId")));
    }

    private static void run(Runnable call) {
        guard(() -> {
            call.run();
            return null;
        });
    }

    private static <T> T guard(java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (CancellationMissingException exception) {
            throw CamundaError.bpmnError(CANCELLATION_MISSING, exception.getMessage());
        }
    }

    private static String text(Map<String, Object> variables, String name) {
        Object value = variables.get(name);
        return value == null ? null : value.toString();
    }

    private static UUID uuid(Map<String, Object> variables, String name) {
        String value = text(variables, name);
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }
}
