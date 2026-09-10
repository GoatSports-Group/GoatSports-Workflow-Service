package iuh.fit.workflowservice.application.dto.submission;

import iuh.fit.workflowservice.application.dto.response.storage.PresignedUrlResponse;

import java.util.List;
import java.util.UUID;

public record OwnerUploadPreparation(
        long processInstanceKey,
        long taskKey,
        UUID ownerApplicationId,
        String userId,
        List<PresignedUrlResponse> presignedUrls
) {
}
