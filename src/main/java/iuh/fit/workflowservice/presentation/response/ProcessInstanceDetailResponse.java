package iuh.fit.workflowservice.presentation.response;

public record ProcessInstanceDetailResponse(
        long processInstanceKey,
        String bpmnProcessId,
        int version,
        long processDefinitionKey,
        String state,
        String startDate,
        String endDate
) {
}
