package iuh.fit.workflowservice.infrastructure.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import iuh.fit.workflowservice.application.dto.request.storage.FileMoveRequest;
import iuh.fit.workflowservice.application.dto.request.storage.MoveFileRequest;
import iuh.fit.workflowservice.application.dto.request.storage.PresignedUrlRequest;
import iuh.fit.workflowservice.application.dto.response.base.BaseResponse;
import iuh.fit.workflowservice.application.dto.response.storage.PresignedUrlResponse;
import iuh.fit.workflowservice.application.exception.ServiceException;
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

    @JobWorker(type = "getPresignedURLs")
    public Map<String, Object> handleGetPresignedURLs(ActivatedJob job) throws ServiceException {
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

        BaseResponse<List<PresignedUrlResponse>> response = storageClient.getPresignedUrl(requests);

        if(response.getError() != null || response.getStatusCode() >= 400) {
            throw new ServiceException(response.getMessage().toString());
        }

        return Map.of("presignedUrls", response.getData());
    }

    @JobWorker(type = "moveFilesR2")
    public Map<String, Object> handleMoveFilesR2(ActivatedJob job) throws ServiceException {
        LOGGER.info("Executing moveFilesR2 for job: {}", job.getKey());

        Map<String, Object> variables = job.getVariablesAsMap();

        List<String> fileRequests = (List<String>) variables.get("documentKeys");
        List<FileMoveRequest> requests = new ArrayList<>();
        if (fileRequests != null) {
            for (String req : fileRequests) {
                requests.add(new FileMoveRequest(req));
            }
        }

        BaseResponse<Void> response = storageClient.moveFile(
                new MoveFileRequest(
                        requests,
                        job.getProcessInstanceKey()
                )
        );

        if(response.getError() != null || response.getStatusCode() >= 400) {
            throw new ServiceException(response.getMessage().toString());
        }

        return Map.of();
    }
}
