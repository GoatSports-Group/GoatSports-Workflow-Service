package iuh.fit.workflowservice.presentation.response;

import iuh.fit.workflowservice.application.dto.progress.OwnerApplicationProgress;

import java.util.List;

public record OwnerApplicationProgressSearchResponse(
        List<OwnerApplicationProgress> items
) { }
