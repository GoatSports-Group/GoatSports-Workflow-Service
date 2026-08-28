package iuh.fit.workflowservice.infrastructure.external.client;

import iuh.fit.workflowservice.application.dto.request.venue.CreateDocumentsRequest;
import iuh.fit.workflowservice.application.dto.request.venue.ReviewOwnerApplicationRequest;
import iuh.fit.workflowservice.application.dto.request.venue.SubmitOwnerApplicationRequest;
import iuh.fit.workflowservice.application.dto.response.base.BaseResponse;
import iuh.fit.workflowservice.application.dto.response.venue.OwnerApplicationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
    name = "GOATSPORTS-VENUE-SERVICE",
    url = "${venue.service.url}",
    path = "/internal"
)
public interface VenueClient {
    @PostMapping("/owner-applications")
    BaseResponse<OwnerApplicationResponse> submitApplication(@RequestBody SubmitOwnerApplicationRequest request);

    @PostMapping("/documents")
    BaseResponse<Void> createDocuments(@RequestBody CreateDocumentsRequest request);

    @PostMapping("/owner-applications/{id}/reject")
    BaseResponse<OwnerApplicationResponse> rejectApplication(
            @PathVariable("id") String id,
            @RequestBody ReviewOwnerApplicationRequest request
    );

    @PostMapping("/owner-applications/{id}/approve")
    BaseResponse<OwnerApplicationResponse> approveApplication(
            @PathVariable("id") String id,
            @RequestBody ReviewOwnerApplicationRequest request
    );

}
