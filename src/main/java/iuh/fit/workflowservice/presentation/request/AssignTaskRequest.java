package iuh.fit.workflowservice.presentation.request;

import jakarta.validation.constraints.NotBlank;

public record AssignTaskRequest(
        @NotBlank(message = "Assignee cannot be blank")
        String assignee
) {
}
