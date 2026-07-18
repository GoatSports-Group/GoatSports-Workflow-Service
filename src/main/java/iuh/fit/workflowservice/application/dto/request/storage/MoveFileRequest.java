package iuh.fit.workflowservice.application.dto.request.storage;

import java.util.List;

public record MoveFileRequest(
        List<FileMoveRequest> requests,
        Long processInstanceKey
) {}