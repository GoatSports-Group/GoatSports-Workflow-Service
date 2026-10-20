package iuh.fit.workflowservice.infrastructure.external.client;

import feign.RequestInterceptor;
import iuh.fit.workflowservice.application.dto.response.base.BaseResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(
        name = "GOATSPORTS-VENUE-SERVICE",
        url = "${venue.service.url}",
        path = "/internal/booking-cancellations",
        configuration = VenueCancellationClient.Config.class
)
public interface VenueCancellationClient {
    @PutMapping("/{cancellationId}/state")
    BaseResponse<Object> applyState(@PathVariable UUID cancellationId, @RequestBody StateRequest request);

    @PostMapping("/{cancellationId}/refund")
    BaseResponse<RefundResponse> createRefund(@PathVariable UUID cancellationId, @RequestBody RefundRequest request);

    @PostMapping("/{cancellationId}/owner-reminder")
    BaseResponse<Object> remindOwner(@PathVariable UUID cancellationId, @RequestBody ReminderRequest request);

    record StateRequest(UUID bookingId, String status, long version, UUID processedBy, String decisionNote,
                        String refundFailureReason) {
    }

    record RefundRequest(UUID bookingId, UUID processedBy) {
    }

    record RefundResponse(UUID refundId, String status, String failureReason) {
    }

    record ReminderRequest(UUID bookingId) {
    }

    class Config {
        @Bean
        RequestInterceptor venueServiceIdentity(@Value("${app.jwt.secret}") String gatewaySecret) {
            return template -> template.header("X-Gateway-Secret", gatewaySecret);
        }
    }
}
