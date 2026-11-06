package iuh.fit.workflowservice.application.port;

import java.util.UUID;

/** venue-service ghi trạng thái theo mốc giờ của process. */
public interface VenueBookingLifecyclePort {
    /**
     * @param status EXPIRED, NO_SHOW hoặc COMPLETED
     * @return trạng thái của đơn sau khi ghi; venue-service không ghi được (đơn đã đi bước khác) thì là trạng thái
     *         hiện tại, để process rẽ đúng nhánh
     * @throws iuh.fit.workflowservice.application.exception.BookingMissingException venue-service không còn đơn
     */
    String applyState(UUID bookingId, String status);
}
