package iuh.fit.workflowservice.presentation.controller;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.api.search.response.Variable;
import iuh.fit.shared.annotation.AuditLog;
import iuh.fit.workflowservice.application.exception.ServiceException;
import iuh.fit.workflowservice.application.port.security.CurrentUserProvider;
import iuh.fit.workflowservice.application.port.util.ObjectMapperPort;
import iuh.fit.workflowservice.application.util.BaseUtil;
import iuh.fit.workflowservice.presentation.request.CompleteReviewRequest;
import iuh.fit.workflowservice.presentation.request.CompleteTaskRequest;
import iuh.fit.workflowservice.presentation.response.ProcessInstanceResponse;
import iuh.fit.workflowservice.presentation.response.UserTaskResponse;
import iuh.fit.workflowservice.presentation.request.StartProcessRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
@Slf4j
public class WorkflowController {

    private final CamundaClient camundaClient;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapperPort objectMapper;

    private final String BPMN_ID = "Process_VenueOwnerRegistration";

    @PostMapping("/start")
    @AuditLog(action = "CREATE_APPLICATION", description = "Thực hiện quy trình tạo đơn đăng ký chủ sân")
    public ResponseEntity<ProcessInstanceResponse> startVenueOwnerApplicationRegistrationProcess(
            @Valid @RequestBody StartProcessRequest request
    ) throws ServiceException
    {
        String userIdStr = currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new ServiceException("Bạn phải đăng nhập để thực hiện chức năng này."));
        UUID userId = BaseUtil.uuid(userIdStr);

        request.variables().put("userId", userId.toString());

        ProcessInstanceEvent event = camundaClient.newCreateInstanceCommand()
                .bpmnProcessId(BPMN_ID)
                .latestVersion()
                .variables(request.variables())
                .send()
                .join();

        ProcessInstanceResponse response = new ProcessInstanceResponse(
                event.getProcessInstanceKey(),
                event.getBpmnProcessId(),
                event.getVersion(),
                event.getProcessDefinitionKey()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/instances/{instanceKey}/variables")
    public ResponseEntity<Map<String, Object>> getProcessInstanceVariables(@PathVariable long instanceKey) {
        SearchResponse<Variable> searchResponse = camundaClient.newVariableSearchRequest()
                .filter(f -> f.scopeKey(instanceKey))
                .send()
                .join();

        Map<String, Object> variablesMap = new HashMap<>();

        if (searchResponse.items() != null) {
            for (Variable variable : searchResponse.items()) {
                variablesMap.put(variable.getName(), objectMapper.read(variable.getValue()));
            }
        }

        return ResponseEntity.ok(variablesMap);
    }

    @GetMapping("/instances/{instanceKey}/tasks")
    public ResponseEntity<UserTaskResponse> getTasksByProcessInstance(@PathVariable long instanceKey) {

        SearchResponse<UserTask> searchResponse = camundaClient
                .newUserTaskSearchRequest()
                .filter(f -> f.processInstanceKey(instanceKey))
                .send()
                .join();

        UserTask userTask = searchResponse.items().stream()
                .filter(task -> task.getProcessInstanceKey() == instanceKey)
                .filter(task -> task.getState() != null && task.getState() == UserTaskState.CREATED)
                .findFirst()
                .orElse(null);

        UserTaskResponse response = null;
        if(userTask != null) {
            response = new UserTaskResponse(
                userTask.getUserTaskKey(),
                userTask.getName(),
                userTask.getElementId(),
                userTask.getAssignee(),
                userTask.getState() != null ? userTask.getState().name() : null,
                userTask.getProcessInstanceKey(),
                userTask.getBpmnProcessId()
            );
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/tasks/{taskKey}/complete")
    public ResponseEntity<Void> completeTask(
            @PathVariable long taskKey,
            @Valid @RequestBody CompleteTaskRequest request
    ) {
        camundaClient.newCompleteUserTaskCommand(taskKey)
                .variables(request.variables())
                .send()
                .join();

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PostMapping("/owner-applications/{ownerApplicationId}/complete-review")
    public ResponseEntity<Void> completeAdminReview(
            @PathVariable String ownerApplicationId,
            @Valid @RequestBody CompleteReviewRequest request
    ) throws ServiceException
    {
        log.info("Received request to complete admin review for application: {}, approved: {}", ownerApplicationId, request.approved());

        String reviewerIdStr = currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new ServiceException("Bạn phải đăng nhập để thực hiện chức năng này."));

        SearchResponse<UserTask> searchResponse = camundaClient.newUserTaskSearchRequest()
                .filter(f -> f.elementId("Task_AdminReview")
                              .state(UserTaskState.CREATED))
                .send()
                .join();

        if (searchResponse.items() == null || searchResponse.items().isEmpty()) {
            throw new ServiceException("Không tìm thấy task duyệt đơn chủ sân nào đang hoạt động");
        }

        for (UserTask task : searchResponse.items()) {
            long instanceKey = task.getProcessInstanceKey();

            SearchResponse<Variable> variablesResponse = camundaClient.newVariableSearchRequest()
                    .filter(f -> f.scopeKey(instanceKey))
                    .send()
                    .join();

            boolean matches = false;
            Object ownerAppObj = null;

            if (variablesResponse.items() != null) {
                for (Variable variable : variablesResponse.items()) {
                    if ("ownerApplication".equals(variable.getName())) {
                        ownerAppObj = objectMapper.read(variable.getValue());
                        if (variable.getValue() != null && variable.getValue().contains(ownerApplicationId)) {
                            matches = true;
                        }
                    }
                }
            }

            if (matches) {
                log.info("Found matching user task key {} for owner application {}", task.getUserTaskKey(), ownerApplicationId);

                Map<String, Object> variables = new HashMap<>();
                variables.put("approved", request.approved());
                variables.put("reviewerId", reviewerIdStr);

                if (ownerAppObj instanceof Map) {
                    Map<String, Object> ownerAppMap = (Map<String, Object>) ownerAppObj;

                    ownerAppMap.put("status", request.approved() ? "APPROVED" : "REJECTED");
                    ownerAppMap.put("rejectReason", request.rejectReason());
                    ownerAppMap.put("reviewerId", reviewerIdStr);

                    variables.put("ownerApplication", ownerAppMap);
                }

                camundaClient.newCompleteUserTaskCommand(task.getUserTaskKey())
                        .variables(variables)
                        .send()
                        .join();

                log.info("Successfully completed Admin Review task for owner application {} with approved = {}", ownerApplicationId, request.approved());
                return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
            }
        }

        throw new ServiceException("Không tìm thấy task duyệt cho đơn đăng ký này");
    }
}
