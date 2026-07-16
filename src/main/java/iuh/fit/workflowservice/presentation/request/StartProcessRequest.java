package iuh.fit.workflowservice.presentation.request;

import java.util.Map;

public record StartProcessRequest(
        Map<String, Object> variables
) {
}
