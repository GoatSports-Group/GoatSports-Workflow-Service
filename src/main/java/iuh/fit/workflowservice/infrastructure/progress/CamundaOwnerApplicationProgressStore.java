package iuh.fit.workflowservice.infrastructure.progress;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.api.search.response.Variable;
import iuh.fit.workflowservice.application.dto.progress.OwnerApplicationProcess;
import iuh.fit.workflowservice.application.dto.progress.OwnerApplicationProgress;
import iuh.fit.workflowservice.application.port.progress.OwnerApplicationProgressStore;
import iuh.fit.workflowservice.application.port.util.ObjectMapperPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CamundaOwnerApplicationProgressStore implements OwnerApplicationProgressStore {
    private static final String PROCESS_ID = "Process_VenueOwnerRegistration";
    private static final String APPLICATION_ID = "ownerApplicationId";
    private static final String USER_ID = "userId";
    private static final String RECEIVED_AT = "adminReceivedAt";
    private static final String VIEWED_AT = "adminViewedAt";
    private static final String VIEWED_BY = "adminViewedBy";
    private static final String OWNER_APPLICATION = "ownerApplication";
    private static final String ADMIN_REVIEW_TASK = "Task_AdminReview";
    private static final List<String> PROGRESS_VARIABLES = List.of(
            APPLICATION_ID,
            USER_ID,
            RECEIVED_AT,
            VIEWED_AT
    );

    private final CamundaClient camundaClient;
    private final ObjectMapperPort objectMapper;

    @Override
    public List<OwnerApplicationProgress> findOwnedByIds(
            Collection<UUID> ownerApplicationIds,
            UUID ownerId
    ) {
        if (ownerApplicationIds.isEmpty()) {
            return List.of();
        }

        Set<UUID> requestedIds = Set.copyOf(ownerApplicationIds);
        List<String> serializedApplicationIds = requestedIds.stream()
                .map(applicationId -> "\"" + applicationId + "\"")
                .toList();

        SearchResponse<Variable> applicationIdVariables = camundaClient.newVariableSearchRequest()
                .filter(filter -> filter
                        .name(APPLICATION_ID)
                        .value(property -> property.in(serializedApplicationIds)))
                .send()
                .join();

        List<Long> candidateProcessKeys = applicationIdVariables.items().stream()
                .map(Variable::getProcessInstanceKey)
                .distinct()
                .toList();
        if (candidateProcessKeys.isEmpty()) {
            log.debug("No Camunda process found for {} requested owner applications", requestedIds.size());
            return List.of();
        }

        SearchResponse<ProcessInstance> processes = camundaClient.newProcessInstanceSearchRequest()
                .filter(filter -> filter
                        .processDefinitionId(PROCESS_ID)
                        .processInstanceKey(property -> property.in(candidateProcessKeys)))
                .send()
                .join();

        List<Long> processKeys = processes.items().stream()
                .map(ProcessInstance::getProcessInstanceKey)
                .distinct()
                .toList();
        if (processKeys.isEmpty()) {
            log.debug("No owner registration process found for {} candidate processes", candidateProcessKeys.size());
            return List.of();
        }

        SearchResponse<Variable> variables = camundaClient.newVariableSearchRequest()
                .filter(filter -> filter
                        .processInstanceKey(property -> property.in(processKeys))
                        .name(property -> property.in(PROGRESS_VARIABLES)))
                .send()
                .join();

        Map<Long, ProgressAccumulator> progressByProcess = new LinkedHashMap<>();
        processKeys.forEach(key -> progressByProcess.put(key, new ProgressAccumulator()));
        for (Variable variable : variables.items()) {
            ProgressAccumulator progress = progressByProcess.get(variable.getProcessInstanceKey());
            if (progress != null) {
                progress.accept(variable.getName(), readString(variable));
            }
        }

        List<OwnerApplicationProgress> result = progressByProcess.values().stream()
                .map(progress -> progress.toProgress(requestedIds, ownerId))
                .flatMap(Optional::stream)
                .toList();
        log.debug(
                "Loaded owner application progress: requested={}, matchedProcesses={}, authorized={}",
                requestedIds.size(),
                processKeys.size(),
                result.size()
        );
        return result;
    }

    @Override
    public Optional<OwnerApplicationProcess> findByOwnerApplicationId(UUID ownerApplicationId) {
        SearchResponse<ProcessInstance> response = camundaClient.newProcessInstanceSearchRequest()
                .filter(filter -> filter
                        .processDefinitionId(PROCESS_ID)
                        .variables(Map.of(APPLICATION_ID, ownerApplicationId.toString())))
                .send()
                .join();

        if (response.items().isEmpty()) {
            return findLegacyActiveProcess(ownerApplicationId);
        }

        return createProcess(response.items().getFirst().getProcessInstanceKey(), ownerApplicationId);
    }

    @Override
    public boolean markViewedIfAbsent(
            long processInstanceKey,
            UUID ownerApplicationId,
            String viewedAt,
            UUID viewerId
    ) {
        if (findVariable(processInstanceKey, VIEWED_AT).isPresent()) {
            return false;
        }

        camundaClient.newSetVariablesCommand(processInstanceKey)
                .variables(Map.of(
                        APPLICATION_ID, ownerApplicationId.toString(),
                        VIEWED_AT, viewedAt,
                        VIEWED_BY, viewerId.toString()
                ))
                .send()
                .join();
        return true;
    }

    @Override
    public boolean markReceivedIfAbsent(long processInstanceKey, String receivedAt) {
        if (findVariable(processInstanceKey, RECEIVED_AT).isPresent()) {
            return false;
        }

        camundaClient.newSetVariablesCommand(processInstanceKey)
                .variable(RECEIVED_AT, receivedAt)
                .send()
                .join();
        return true;
    }

    private Optional<OwnerApplicationProcess> createProcess(long processInstanceKey, UUID ownerApplicationId) {
        String userId = findVariable(processInstanceKey, USER_ID).orElse(null);
        if (userId == null) return Optional.empty();
        return Optional.of(new OwnerApplicationProcess(
                processInstanceKey,
                ownerApplicationId,
                UUID.fromString(userId)
        ));
    }

    private Optional<OwnerApplicationProcess> findLegacyActiveProcess(UUID ownerApplicationId) {
        SearchResponse<UserTask> tasks = camundaClient.newUserTaskSearchRequest()
                .filter(filter -> filter
                        .elementId(ADMIN_REVIEW_TASK)
                        .state(UserTaskState.CREATED))
                .send()
                .join();

        for (UserTask task : tasks.items()) {
            Optional<Object> ownerApplication = findVariableValue(
                    task.getProcessInstanceKey(),
                    OWNER_APPLICATION
            );
            if (ownerApplication.isEmpty()) continue;
            if (!(ownerApplication.get() instanceof Map<?, ?> application)) continue;

            Object applicationId = application.get(APPLICATION_ID);
            if (applicationId != null && ownerApplicationId.toString().equals(applicationId.toString())) {
                return createProcess(task.getProcessInstanceKey(), ownerApplicationId);
            }
        }
        return Optional.empty();
    }

    private Optional<String> findVariable(long processInstanceKey, String name) {
        return findVariableValue(processInstanceKey, name).map(Object::toString);
    }

    private Optional<Object> findVariableValue(long processInstanceKey, String name) {
        SearchResponse<Variable> response = camundaClient.newVariableSearchRequest()
                .filter(filter -> filter
                        .processInstanceKey(processInstanceKey)
                        .name(name))
                .send()
                .join();

        return response.items().stream()
                .findFirst()
                .map(variable -> objectMapper.read(variable.getValue()));
    }

    private String readString(Variable variable) {
        Object value = objectMapper.read(variable.getValue());
        return value == null ? null : value.toString();
    }

    private static final class ProgressAccumulator {
        private UUID ownerApplicationId;
        private UUID ownerId;
        private String receivedAt;
        private String viewedAt;

        private void accept(String name, String value) {
            switch (name) {
                case APPLICATION_ID -> ownerApplicationId = value == null ? null : UUID.fromString(value);
                case USER_ID -> ownerId = value == null ? null : UUID.fromString(value);
                case RECEIVED_AT -> receivedAt = value;
                case VIEWED_AT -> viewedAt = value;
                default -> { }
            }
        }

        private Optional<OwnerApplicationProgress> toProgress(
                Set<UUID> requestedIds,
                UUID currentOwnerId
        ) {
            if (ownerApplicationId == null
                    || ownerId == null
                    || !requestedIds.contains(ownerApplicationId)
                    || !currentOwnerId.equals(ownerId)) {
                return Optional.empty();
            }
            return Optional.of(new OwnerApplicationProgress(ownerApplicationId, receivedAt, viewedAt));
        }
    }
}
