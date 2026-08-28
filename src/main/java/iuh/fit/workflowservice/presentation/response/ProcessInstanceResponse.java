package iuh.fit.workflowservice.presentation.response;

public record ProcessInstanceResponse(
        long processInstanceKey,
        String bpmnProcessId,
        int version,
        long processDefinitionKey,
        String registrationAccessToken
) {
}
