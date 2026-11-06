package iuh.fit.workflowservice.infrastructure.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.exception.CamundaError;
import iuh.fit.workflowservice.application.exception.BookingMissingException;
import iuh.fit.workflowservice.application.port.VenueBookingLifecyclePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Worker của process "Vòng đời đặt sân": ghi trạng thái theo mốc giờ sang venue-service và trả về trạng thái thật
 * của đơn (biến bookingStatus) để gateway sau đó rẽ đúng nhánh. Lỗi tạm để Camunda thử lại theo retries; venue-service
 * không còn đơn thì ném lỗi BPMN, process kết thúc ở nhánh "Đơn không còn".
 */
@Component
@RequiredArgsConstructor
public class BookingLifecycleJobWorker {
    static final String BOOKING_MISSING = "BOOKING_NOT_FOUND";
    private static final long RETRY_BACKOFF_MS = 5_000;

    private final VenueBookingLifecyclePort venue;

    @JobWorker(type = "booking-lifecycle.apply-state", retryBackoff = RETRY_BACKOFF_MS)
    public Map<String, Object> applyState(ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        UUID bookingId = UUID.fromString(variables.get("bookingId").toString());
        try {
            return Map.of("bookingStatus", venue.applyState(bookingId, variables.get("status").toString()));
        } catch (BookingMissingException exception) {
            throw CamundaError.bpmnError(BOOKING_MISSING, exception.getMessage());
        }
    }
}
