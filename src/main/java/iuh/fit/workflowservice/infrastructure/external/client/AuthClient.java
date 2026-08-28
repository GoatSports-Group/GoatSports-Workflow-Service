package iuh.fit.workflowservice.infrastructure.external.client;

import iuh.fit.workflowservice.application.dto.response.base.BaseResponse;
import iuh.fit.workflowservice.application.dto.request.auth.RegisterVenueOwnerRequest;
import iuh.fit.workflowservice.application.dto.response.auth.RegisteredUserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
    name = "GOATSPORTS-AUTH-SERVICE",
    url = "${auth.service.url}",
    path = "/internal/auth"
)
public interface AuthClient {
    @PostMapping("/venue-owner/register")
    BaseResponse<RegisteredUserResponse> registerVenueOwner(@RequestBody RegisterVenueOwnerRequest request);
}
