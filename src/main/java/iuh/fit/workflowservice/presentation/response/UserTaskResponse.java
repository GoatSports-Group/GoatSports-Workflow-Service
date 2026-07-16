package iuh.fit.workflowservice.presentation.response;

public record UserTaskResponse(
        long key,
        String name,
        String elementId,
        String assignee,
        String state,
        long processInstanceKey,
        String bpmnProcessId
) {
}
