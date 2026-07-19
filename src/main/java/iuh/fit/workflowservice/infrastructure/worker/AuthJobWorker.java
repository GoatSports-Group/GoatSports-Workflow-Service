package iuh.fit.workflowservice.infrastructure.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import iuh.fit.workflowservice.application.dto.response.base.BaseResponse;
import iuh.fit.workflowservice.application.dto.response.venue.OwnerApplicationResponse;
import iuh.fit.workflowservice.application.exception.ServiceException;
import iuh.fit.workflowservice.infrastructure.external.client.AuthClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AuthJobWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthJobWorker.class);

    private final AuthClient authClient;

    @JobWorker(type = "assignRole")
    public Map<String, Object> handleAssignRole(ActivatedJob job) throws ServiceException {
        LOGGER.info("Executing assignRole for job: {}", job.getKey());

        Map<String, Object> variables = job.getVariablesAsMap();
        String userId = getUserId(variables);

        BaseResponse<Void> response = authClient.assignVenueOwnerRole(userId);

        if (response.getError() != null || response.getStatusCode() >= 400) {
            throw new ServiceException(response.getMessage().toString());
        }

        return Map.of();
    }

    private String getUserId(Map<String, Object> variables) {
        Object ownerApp = variables.get("ownerApplication");

        return switch (ownerApp) {
            case Map<?, ?> map -> {
                Object val = map.get("userId");
                yield val != null ? val.toString() : null;
            }
            case OwnerApplicationResponse response -> response.userId() != null ? response.userId().toString() : null;
            case null, default -> null;
        };
    }
}
