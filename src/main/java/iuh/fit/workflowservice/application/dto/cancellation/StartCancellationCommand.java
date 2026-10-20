package iuh.fit.workflowservice.application.dto.cancellation;

import java.util.UUID;

/**
 * venue-service vừa ghi yêu cầu hủy (PENDING) và xin mở process. {@code resumeStatus}/{@code refundId} chỉ có khi
 * venue-service nhận nuôi yêu cầu hủy cũ (tạo trước khi có process): process bắt đầu ngay tại bước tương ứng.
 *
 * @param playStartAt ISO-8601 có offset, mốc tự chấp thuận nếu chủ sân không phản hồi
 * @param decisionNote ghi chú quyết định khi venue-service tự duyệt (AUTOMATIC)
 */
public record StartCancellationCommand(
        UUID cancellationId,
        UUID bookingId,
        String reviewMode,
        double refundAmount,
        String playStartAt,
        String decisionNote,
        String resumeStatus,
        UUID refundId
) {
}
