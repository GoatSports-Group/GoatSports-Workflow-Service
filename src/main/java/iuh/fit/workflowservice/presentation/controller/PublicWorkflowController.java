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
import iuh.fit.workflowservice.presentation.request.CompleteTaskRequest;
import iuh.fit.workflowservice.presentation.request.StartProcessRequest;
import iuh.fit.workflowservice.presentation.response.ProcessInstanceResponse;
import iuh.fit.workflowservice.presentation.response.UserTaskResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workflows/public")
@RequiredArgsConstructor
public class PublicWorkflowController {

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
        String registrationAccessToken = UUID.randomUUID().toString();
        request.variables().put("registrationAccessToken", registrationAccessToken);

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
                event.getProcessDefinitionKey(),
                registrationAccessToken
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/instances/{instanceKey}/variables")
    public ResponseEntity<Map<String, Object>> getProcessInstanceVariables(
            @PathVariable long instanceKey,
            @RequestHeader(name = "X-Registration-Token", required = false) String registrationToken
    ) throws ServiceException {
        validateProcessAccess(instanceKey, registrationToken);
        SearchResponse<Variable> searchResponse = camundaClient.newVariableSearchRequest()
                .filter(f -> f.scopeKey(instanceKey))
                .send()
                .join();

        Map<String, Object> variablesMap = new HashMap<>();

        if (searchResponse.items() != null) {
            for (Variable variable : searchResponse.items()) {
                if (Set.of("password", "confirmPassword", "registrationAccessToken").contains(variable.getName())) {
                    continue;
                }
                variablesMap.put(variable.getName(), objectMapper.read(variable.getValue()));
            }
        }

        return ResponseEntity.ok(variablesMap);
    }

    @GetMapping("/instances/{instanceKey}/tasks")
    public ResponseEntity<UserTaskResponse> getTasksByProcessInstance(
            @PathVariable long instanceKey,
            @RequestHeader(name = "X-Registration-Token", required = false) String registrationToken
    ) throws ServiceException {
        validateProcessAccess(instanceKey, registrationToken);

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
            @RequestHeader(name = "X-Registration-Token", required = false) String registrationToken,
            @Valid @RequestBody CompleteTaskRequest request
    ) throws ServiceException {
        SearchResponse<UserTask> taskSearch = camundaClient.newUserTaskSearchRequest()
                .filter(filter -> filter.userTaskKey(taskKey))
                .send()
                .join();
        UserTask task = taskSearch.items().stream().findFirst()
                .orElseThrow(() -> new ServiceException("Không tìm thấy task cần hoàn tất."));
        validateProcessAccess(task.getProcessInstanceKey(), registrationToken);

        camundaClient.newCompleteUserTaskCommand(taskKey)
                .variables(request.variables())
                .send()
                .join();

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private void validateProcessAccess(long instanceKey, String registrationToken) throws ServiceException {
        if (currentUserProvider.getCurrentUserId().isPresent()) {
            return;
        }
        if (registrationToken == null || registrationToken.isBlank()) {
            throw new ServiceException("Registration token không hợp lệ.");
        }

        SearchResponse<Variable> variables = camundaClient.newVariableSearchRequest()
                .filter(filter -> filter.scopeKey(instanceKey).name("registrationAccessToken"))
                .send()
                .join();
        boolean authorized = variables.items().stream().anyMatch(variable -> {
            Object storedToken = objectMapper.read(variable.getValue());
            return registrationToken.equals(storedToken);
        });
        if (!authorized) {
            throw new ServiceException("Registration token không hợp lệ.");
        }
    }
}
