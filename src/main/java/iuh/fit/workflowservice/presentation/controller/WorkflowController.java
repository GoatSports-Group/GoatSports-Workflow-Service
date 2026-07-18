package iuh.fit.workflowservice.presentation.controller;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.api.search.response.Variable;
import iuh.fit.workflowservice.application.exception.ServiceException;
import iuh.fit.workflowservice.application.port.security.CurrentUserProvider;
import iuh.fit.workflowservice.application.util.BaseUtil;
import iuh.fit.workflowservice.presentation.request.CompleteTaskRequest;
import iuh.fit.workflowservice.presentation.response.ProcessInstanceResponse;
import iuh.fit.workflowservice.presentation.response.UserTaskResponse;
import iuh.fit.workflowservice.presentation.request.StartProcessRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final CamundaClient camundaClient;
    private final CurrentUserProvider currentUserProvider;

    private final String BPMN_ID = "Process_VenueOwnerRegistration";

    @PostMapping("/start")
    public ResponseEntity<ProcessInstanceResponse> startProcess(
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
                variablesMap.put(variable.getName(), BaseUtil.convert(variable.getValue()));
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

//    @PutMapping("/tasks/{taskKey}/assign")
//    public ResponseEntity<Void> assignTask(
//            @PathVariable long taskKey,
//            @Valid @RequestBody AssignTaskRequest request
//    ) {
//        camundaClient.newAssignUserTaskCommand(taskKey)
//                .assignee(request.assignee())
//                .send()
//                .join();
//
//        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
//    }
//
//    @GetMapping("/tasks")
//    public ResponseEntity<List<UserTaskResponse>> listUserTasks(
//            @RequestParam(value = "assignee", required = false) String assignee
//    ) {
//        UserTaskSearchRequest searchRequest = camundaClient.newUserTaskSearchRequest();
//        if (assignee != null && !assignee.isBlank()) {
//            searchRequest.filter(f -> f.assignee(assignee));
//        }
//
//        SearchResponse<UserTask> searchResponse = searchRequest.send().join();
//
//        List<UserTaskResponse> responseList = searchResponse.items().stream()
//                .map(task -> new UserTaskResponse(
//                        task.getUserTaskKey(),
//                        task.getName(),
//                        task.getElementId(),
//                        task.getAssignee(),
//                        task.getState() != null ? task.getState().name() : null,
//                        task.getProcessInstanceKey(),
//                        task.getBpmnProcessId()
//                ))
//                .toList();
//
//        return ResponseEntity.ok(responseList);
//    }
//
//    @GetMapping("/instances/{instanceKey}")
//    public ResponseEntity<ProcessInstanceDetailResponse> getProcessInstance(@PathVariable long instanceKey) {
//        SearchResponse<ProcessInstance> searchResponse = camundaClient.newProcessInstanceSearchRequest()
//                .filter(f -> f.processInstanceKey(instanceKey))
//                .send()
//                .join();
//
//        List<ProcessInstance> items = searchResponse.items();
//        if (items == null || items.isEmpty()) {
//            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
//        }
//
//        ProcessInstance instance = items.getFirst();
//
//        ProcessInstanceDetailResponse response = new ProcessInstanceDetailResponse(
//                instance.getProcessInstanceKey(),
//                instance.getProcessDefinitionId(),
//                instance.getProcessDefinitionVersion(),
//                instance.getProcessDefinitionKey(),
//                instance.getState() != null ? instance.getState().name() : null,
//                instance.getStartDate().toString(),
//                instance.getEndDate().toString()
//        );
//
//        return ResponseEntity.ok(response);
//    }


}
