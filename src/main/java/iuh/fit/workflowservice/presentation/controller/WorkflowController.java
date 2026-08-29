package iuh.fit.workflowservice.presentation.controller;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.api.search.response.Variable;
import iuh.fit.workflowservice.application.exception.ServiceException;
import iuh.fit.workflowservice.application.port.security.CurrentUserProvider;
import iuh.fit.workflowservice.application.port.util.ObjectMapperPort;
import iuh.fit.workflowservice.presentation.request.CompleteReviewRequest;
import iuh.fit.workflowservice.presentation.request.CompleteTaskRequest;
import iuh.fit.workflowservice.presentation.request.StartProcessRequest;
import iuh.fit.workflowservice.presentation.response.ProcessInstanceResponse;
import iuh.fit.workflowservice.presentation.response.UserTaskResponse;
import iuh.fit.shared.annotation.AuditLog;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
@Slf4j
public class WorkflowController {
    private static final String OWNER_APPLICATION_PROCESS_ID = "Process_VenueOwnerRegistration";
    private static final String USER_UPLOAD_TASK_ID = "Task_UserUpload";
    private static final String INSTANCE_STATE = "workflowInstanceState";
    private static final String INSTANCE_HAS_INCIDENT = "workflowHasIncident";

    private final CamundaClient camundaClient;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapperPort objectMapper;

    @PostMapping("/start")
    @PreAuthorize("hasRole('VENUE_OWNER')")
    @AuditLog(action = "CREATE_APPLICATION", description = "Thực hiện quy trình tạo đơn đăng ký chủ sân")
    public ResponseEntity<ProcessInstanceResponse> startOwnerApplication(
            @Valid @RequestBody StartProcessRequest request
    ) throws ServiceException {
        Map<String, Object> variables = new HashMap<>(request.variables());
        variables.put("userId", currentUserId());

        ProcessInstanceEvent event = camundaClient.newCreateInstanceCommand()
                .bpmnProcessId(OWNER_APPLICATION_PROCESS_ID)
                .latestVersion()
                .variables(variables)
                .send()
                .join();

        return ResponseEntity.status(HttpStatus.CREATED).body(new ProcessInstanceResponse(
                event.getProcessInstanceKey(), event.getBpmnProcessId(), event.getVersion(),
                event.getProcessDefinitionKey()
        ));
    }

    @GetMapping("/instances/{instanceKey}/variables")
    @PreAuthorize("hasRole('VENUE_OWNER')")
    public ResponseEntity<Map<String, Object>> getProcessInstanceVariables(
            @PathVariable long instanceKey
    ) throws ServiceException {
        assertOwner(instanceKey);
        Map<String, Object> variables = new HashMap<>();
        findVariables(instanceKey).items().forEach(variable ->
                variables.put(variable.getName(), objectMapper.read(variable.getValue()))
        );
        findProcessInstance(instanceKey).ifPresent(process -> {
            variables.put(INSTANCE_STATE, String.valueOf(process.getState()));
            variables.put(INSTANCE_HAS_INCIDENT, Boolean.TRUE.equals(process.getHasIncident()));
        });
        return ResponseEntity.ok(variables);
    }

    @GetMapping("/instances/{instanceKey}/tasks")
    @PreAuthorize("hasRole('VENUE_OWNER')")
    public ResponseEntity<UserTaskResponse> getTask(@PathVariable long instanceKey) throws ServiceException {
        assertOwner(instanceKey);
        UserTask task = camundaClient.newUserTaskSearchRequest()
                .filter(filter -> filter.processInstanceKey(instanceKey)
                        .elementId(USER_UPLOAD_TASK_ID)
                        .state(UserTaskState.CREATED))
                .send().join().items().stream().findFirst().orElse(null);
        return ResponseEntity.ok(task == null ? null : toResponse(task));
    }

    @PostMapping("/tasks/{taskKey}/complete")
    @PreAuthorize("hasRole('VENUE_OWNER')")
    public ResponseEntity<Void> completeOwnerUpload(
            @PathVariable long taskKey,
            @Valid @RequestBody CompleteTaskRequest request
    ) throws ServiceException {
        UserTask task = camundaClient.newUserTaskSearchRequest()
                .filter(filter -> filter.userTaskKey(taskKey)
                        .elementId(USER_UPLOAD_TASK_ID)
                        .state(UserTaskState.CREATED))
                .send().join().items().stream().findFirst()
                .orElseThrow(() -> new ServiceException("Không tìm thấy bước tải hồ sơ đang chờ."));
        assertOwner(task.getProcessInstanceKey());
        camundaClient.newCompleteUserTaskCommand(taskKey)
                .variables(request.variables()).send().join();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/owner-applications/{ownerApplicationId}/complete-review")
    public ResponseEntity<Void> completeAdminReview(
            @PathVariable String ownerApplicationId,
            @Valid @RequestBody CompleteReviewRequest request
    ) throws ServiceException {
        String reviewerId = currentUserId();
        SearchResponse<UserTask> response = camundaClient.newUserTaskSearchRequest()
                .filter(filter -> filter.elementId("Task_AdminReview").state(UserTaskState.CREATED))
                .send().join();

        for (UserTask task : response.items()) {
            Map<String, Object> ownerApplication = findOwnerApplication(task.getProcessInstanceKey());
            if (ownerApplication == null || !ownerApplicationId.equals(String.valueOf(ownerApplication.get("ownerApplicationId")))) {
                continue;
            }
            ownerApplication.put("status", request.approved() ? "APPROVED" : "REJECTED");
            ownerApplication.put("rejectReason", request.rejectReason());
            ownerApplication.put("reviewerId", reviewerId);
            camundaClient.newCompleteUserTaskCommand(task.getUserTaskKey())
                    .variables(Map.of(
                            "approved", request.approved(),
                            "reviewerId", reviewerId,
                            "ownerApplication", ownerApplication
                    ))
                    .send().join();
            return ResponseEntity.noContent().build();
        }
        throw new ServiceException("Không tìm thấy bước duyệt cho đơn đăng ký này.");
    }

    private SearchResponse<Variable> findVariables(long instanceKey) {
        return camundaClient.newVariableSearchRequest()
                .filter(filter -> filter.scopeKey(instanceKey)).send().join();
    }

    private Optional<ProcessInstance> findProcessInstance(long instanceKey) {
        return camundaClient.newProcessInstanceSearchRequest()
                .filter(filter -> filter.processInstanceKey(instanceKey))
                .send()
                .join()
                .items()
                .stream()
                .findFirst();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findOwnerApplication(long instanceKey) {
        return findVariables(instanceKey).items().stream()
                .filter(variable -> "ownerApplication".equals(variable.getName()))
                .map(variable -> objectMapper.read(variable.getValue()))
                .filter(Map.class::isInstance)
                .map(value -> (Map<String, Object>) value)
                .findFirst().orElse(null);
    }

    private void assertOwner(long instanceKey) throws ServiceException {
        String userId = currentUserId();
        boolean belongsToUser = findVariables(instanceKey).items().stream()
                .filter(variable -> "userId".equals(variable.getName()))
                .map(variable -> objectMapper.read(variable.getValue()))
                .anyMatch(userId::equals);
        if (!belongsToUser) {
            throw new ServiceException("Bạn không có quyền truy cập quy trình này.");
        }
    }

    private String currentUserId() throws ServiceException {
        return currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new ServiceException("Bạn phải đăng nhập để thực hiện chức năng này."));
    }

    private UserTaskResponse toResponse(UserTask task) {
        return new UserTaskResponse(
                task.getUserTaskKey(), task.getName(), task.getElementId(), task.getAssignee(),
                task.getState() != null ? task.getState().name() : null,
                task.getProcessInstanceKey(), task.getBpmnProcessId()
        );
    }
}
