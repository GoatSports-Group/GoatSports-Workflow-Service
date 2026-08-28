package iuh.fit.workflowservice.presentation.request;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record StartProcessRequest(
        @NotNull
        Map<String, Object> variables
) {
}
