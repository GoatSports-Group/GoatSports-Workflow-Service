package iuh.fit.workflowservice.application.dto.lifecycle;

import java.util.UUID;

/**
 * venue-service mở process cho một đơn. Đơn đã qua bước nào (khách tại quầy tạo sẵn CONFIRMED, đơn cũ được nhận
 * nuôi) thì process bắt đầu ngay ở bước tương ứng.
 *
 * @param status        trạng thái đơn lúc mở: PENDING_PAYMENT, CONFIRMED hoặc CHECKED_IN
 * @param remainingPaid đã thu phần còn lại (chỉ có nghĩa khi CONFIRMED)
 * @param holdExpiresAt ISO-8601 có offset, mốc hết giữ chỗ (có thể null khi không còn chờ cọc)
 * @param playEndAt     ISO-8601 có offset, giờ kết thúc lượt chơi
 */
public record StartBookingLifecycleCommand(
        UUID bookingId,
        String status,
        boolean remainingPaid,
        String holdExpiresAt,
        String playEndAt
) {
}
