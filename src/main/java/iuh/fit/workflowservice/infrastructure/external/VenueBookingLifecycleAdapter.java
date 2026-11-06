package iuh.fit.workflowservice.infrastructure.external;

import feign.FeignException;
import iuh.fit.workflowservice.application.dto.response.base.BaseResponse;
import iuh.fit.workflowservice.application.exception.BookingMissingException;
import iuh.fit.workflowservice.application.port.VenueBookingLifecyclePort;
import iuh.fit.workflowservice.infrastructure.external.client.VenueBookingLifecycleClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class VenueBookingLifecycleAdapter implements VenueBookingLifecyclePort {
    private final VenueBookingLifecycleClient client;

    @Override
    public String applyState(UUID bookingId, String status) {
        BaseResponse<Map<String, String>> response;
        try {
            response = client.applyState(bookingId, new VenueBookingLifecycleClient.StateRequest(status));
        } catch (FeignException.NotFound exception) {
            throw new BookingMissingException("venue-service không còn đơn " + bookingId);
        }
        String current = response == null || response.getData() == null ? null : response.getData().get("status");
        if (current == null) throw new IllegalStateException("venue-service không trả trạng thái của đơn " + bookingId);
        return current;
    }
}
