package iuh.fit.workflowservice.presentation.controller;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.api.search.response.Variable;
import iuh.fit.workflowservice.application.exception.ServiceException;
import iuh.fit.workflowservice.application.port.security.CurrentUserProvider;
import iuh.fit.workflowservice.application.port.util.ObjectMapperPort;
import iuh.fit.workflowservice.presentation.request.CompleteReviewRequest;
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
