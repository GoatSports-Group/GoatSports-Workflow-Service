package iuh.fit.workflowservice.infrastructure.external.client;

import iuh.fit.workflowservice.application.dto.response.base.BaseResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(
    name = "GOATSPORTS-AUTH-SERVICE",
    url = "${auth.service.url}",
    path = "/internal/users"
)
public interface AuthClient {
    @PostMapping("/{userId}/roles/venue-owner")
    BaseResponse<Void> assignVenueOwnerRole(@PathVariable("userId") String userId);
}
