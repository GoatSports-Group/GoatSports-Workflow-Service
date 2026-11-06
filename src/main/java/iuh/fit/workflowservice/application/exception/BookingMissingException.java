package iuh.fit.workflowservice.application.exception;

/** venue-service không còn đơn của process (tạo đơn bị rollback sau khi process đã mở). */
public class BookingMissingException extends RuntimeException {
    public BookingMissingException(String message) {
        super(message);
    }
}
