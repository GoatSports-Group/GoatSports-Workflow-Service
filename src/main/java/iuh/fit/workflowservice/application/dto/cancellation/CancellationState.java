package iuh.fit.workflowservice.application.dto.cancellation;

import java.util.UUID;

/**
 * Trạng thái của yêu cầu hủy theo Camunda, đẩy sang venue-service. {@code version} tăng dần theo từng lần process đổi
 * trạng thái (job key); venue-service bỏ qua bản có version nhỏ hơn bản đã ghi để lần ghi trễ không đè trạng thái mới.
 */
public record CancellationState(
        UUID cancellationId,
        UUID bookingId,
        String status,
        long version,
        UUID processedBy,
        String decisionNote,
        String refundFailureReason
) {
}
