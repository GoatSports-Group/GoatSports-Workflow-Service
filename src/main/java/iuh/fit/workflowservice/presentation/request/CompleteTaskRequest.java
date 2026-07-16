package iuh.fit.workflowservice.presentation.request;

import java.util.Map;

public record CompleteTaskRequest(
        Map<String, Object> variables
) {
}
