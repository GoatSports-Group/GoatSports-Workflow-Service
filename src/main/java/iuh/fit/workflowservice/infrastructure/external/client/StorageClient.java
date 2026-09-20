package iuh.fit.workflowservice.infrastructure.external.client;

import iuh.fit.workflowservice.application.dto.request.storage.MoveFileRequest;
import iuh.fit.workflowservice.application.dto.request.storage.PresignedUrlRequest;
import iuh.fit.workflowservice.application.dto.response.base.BaseResponse;
import iuh.fit.workflowservice.application.dto.response.storage.PresignedUrlResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(
    name = "GOATSPORTS-STORAGE-SERVICE",
    url = "${storage.service.url}",
    path = "/internal/files",
    configuration = StorageClientConfig.class
)
public interface StorageClient {
    @PostMapping("/presigned-url")
    BaseResponse<List<PresignedUrlResponse>> getPresignedUrl(
            @RequestBody List<PresignedUrlRequest> requests,
            @RequestParam String userId
    );

    @PostMapping("/move")
    BaseResponse<Void> moveFile(@RequestBody MoveFileRequest requests);
}
