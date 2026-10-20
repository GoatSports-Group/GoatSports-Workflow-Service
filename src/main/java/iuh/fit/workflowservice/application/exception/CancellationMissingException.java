package iuh.fit.workflowservice.application.exception;

/** venue-service không còn yêu cầu hủy của process (ghi DB bị rollback sau khi process đã mở). */
public class CancellationMissingException extends RuntimeException {
    public CancellationMissingException(String message) {
        super(message);
    }
}
