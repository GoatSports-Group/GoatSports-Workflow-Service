package iuh.fit.workflowservice.infrastructure.external.client;

import iuh.fit.workflowservice.application.dto.response.base.BaseResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;
import java.util.UUID;

@FeignClient(
        name = "GOATSPORTS-VENUE-SERVICE",
        contextId = "venueBookingLifecycle",
        url = "${venue.service.url}",
        path = "/internal/booking-lifecycles",
        configuration = VenueCancellationClient.Config.class
)
public interface VenueBookingLifecycleClient {
    @PutMapping("/{bookingId}/state")
    BaseResponse<Map<String, String>> applyState(@PathVariable UUID bookingId, @RequestBody StateRequest request);

    record StateRequest(String status) {
    }
}
