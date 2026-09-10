package iuh.fit.workflowservice.infrastructure.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.UserTaskProperties;
import iuh.fit.workflowservice.application.dto.response.storage.PresignedUrlResponse;
import iuh.fit.workflowservice.application.dto.submission.OwnerUploadPreparation;
import iuh.fit.workflowservice.application.port.submission.OwnerApplicationSubmissionChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OwnerApplicationSubmissionJobWorker {
    private final OwnerApplicationSubmissionChannel submissionChannel;

    @JobWorker(type = "ownerUploadReady")
    public void handleOwnerUploadReady(ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        UserTaskProperties userTask = job.getUserTask();
        if (userTask == null || userTask.getUserTaskKey() == null) {
            throw new IllegalStateException("Camunda did not provide the owner upload task key");
        }

        OwnerUploadPreparation preparation = new OwnerUploadPreparation(
                job.getProcessInstanceKey(),
                userTask.getUserTaskKey(),
                UUID.fromString(requiredString(variables, "ownerApplicationId")),
                requiredString(variables, "userId"),
                presignedUrls(variables.get("presignedUrls"))
        );
        submissionChannel.uploadPreparationReady(preparation);
        log.info(
                "Owner upload task {} is ready for process {}",
                preparation.taskKey(),
                preparation.processInstanceKey()
        );
    }

    @JobWorker(type = "notifySubmissionPreparationFailed")
    public void handlePreparationFailed(ActivatedJob job) {
        submissionChannel.uploadPreparationFailed(
                job.getProcessInstanceKey(),
                errorMessage(job.getVariablesAsMap(), "Không thể chuẩn bị hồ sơ đăng ký chủ sân.")
        );
    }

    @JobWorker(type = "notifyDocumentCreationFailed")
    public void handleDocumentCreationFailed(ActivatedJob job) {
        submissionChannel.documentCreationFailed(
                job.getProcessInstanceKey(),
                errorMessage(job.getVariablesAsMap(), "Không thể tạo tài liệu cho hồ sơ đăng ký chủ sân.")
        );
    }

    @JobWorker(type = "notifyDocumentsCreated")
    public void handleDocumentsCreated(ActivatedJob job) {
        submissionChannel.documentsCreated(job.getProcessInstanceKey());
        log.info("Documents were created for process {}", job.getProcessInstanceKey());
    }

    private List<PresignedUrlResponse> presignedUrls(Object value) {
        if (!(value instanceof List<?> items)) {
            throw new IllegalStateException("Camunda process is missing presigned URLs");
        }

        List<PresignedUrlResponse> result = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> values)) {
                throw new IllegalStateException("Camunda process contains an invalid presigned URL");
            }
            result.add(new PresignedUrlResponse(
                    requiredString(values, "uploadUrl"),
                    requiredString(values, "objectKey")
            ));
        }
        return List.copyOf(result);
    }

    private String requiredString(Map<?, ?> values, String key) {
        Object value = values.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalStateException("Camunda process is missing variable " + key);
        }
        return value.toString();
    }

    private String errorMessage(Map<String, Object> variables, String fallback) {
        Object message = variables.get("workflowErrorMessage");
        return message == null || message.toString().isBlank() ? fallback : message.toString();
    }
}
