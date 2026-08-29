package iuh.fit.workflowservice.infrastructure.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import iuh.fit.workflowservice.application.dto.request.storage.FileMoveRequest;
import iuh.fit.workflowservice.application.dto.request.storage.MoveFileRequest;
import iuh.fit.workflowservice.application.dto.request.storage.PresignedUrlRequest;
import iuh.fit.workflowservice.application.dto.response.base.BaseResponse;
import iuh.fit.workflowservice.application.dto.response.storage.PresignedUrlResponse;
import iuh.fit.workflowservice.infrastructure.external.client.StorageClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class StorageJobWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(StorageJobWorker.class);

    private final StorageClient storageClient;
    private final WorkflowWorkerErrorHandler errorHandler;

    @JobWorker(type = "getPresignedURLs")
    public Map<String, Object> handleGetPresignedURLs(ActivatedJob job) {
        LOGGER.info("Executing getPresignedURLs for job: {}", job.getKey());

        Map<String, Object> variables = job.getVariablesAsMap();
        List<Map<String, Object>> presignedRequests = (List<Map<String, Object>>) variables.get("presignedRequests");

        List<PresignedUrlRequest> requests = new ArrayList<>();
        if (presignedRequests != null) {
            for (Map<String, Object> req : presignedRequests) {
                requests.add(new PresignedUrlRequest(
                    (String) req.get("fileName"),
                    (String) req.get("contentType"),
                    (String) req.get("folder")
                ));
            }
        }

        String userId = (String) variables.get("userId");
        BaseResponse<List<PresignedUrlResponse>> response = errorHandler.execute(
                job, "STORAGE_SERVICE", "GET_PRESIGNED_FAILED",
                () -> storageClient.getPresignedUrl(requests, userId)
        );
        if (response.getData() == null || response.getData().size() != requests.size()) {
            throw errorHandler.fromException(
                    job, "STORAGE_SERVICE", "GET_PRESIGNED_FAILED",
                    new IllegalStateException("Storage Service returned an invalid number of presigned URLs")
            );
        }

        return Map.of("presignedUrls", response.getData());
    }

    @JobWorker(type = "moveFilesR2")
    public Map<String, Object> handleMoveFilesR2(ActivatedJob job) {
        LOGGER.info("Executing moveFilesR2 for job: {}", job.getKey());

        Map<String, Object> variables = job.getVariablesAsMap();

        List<String> fileRequests = (List<String>) variables.get("documentKeys");
        List<FileMoveRequest> requests = new ArrayList<>();
        if (fileRequests != null) {
            for (String req : fileRequests) {
                requests.add(new FileMoveRequest(req));
            }
        }

        errorHandler.execute(
                job, "STORAGE_SERVICE", "MOVE_FILE_FAILED",
                () -> storageClient.moveFile(new MoveFileRequest(requests, job.getProcessInstanceKey()))
        );

        return Map.of();
    }
}
