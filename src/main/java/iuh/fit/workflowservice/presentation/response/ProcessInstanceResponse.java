package iuh.fit.workflowservice.presentation.response;

import iuh.fit.workflowservice.application.dto.response.storage.PresignedUrlResponse;

import java.util.List;
import java.util.UUID;

public record ProcessInstanceResponse(
        long processInstanceKey,
        String bpmnProcessId,
        int version,
        long processDefinitionKey,
        long uploadTaskKey,
        UUID ownerApplicationId,
        List<PresignedUrlResponse> presignedUrls
) {
}
