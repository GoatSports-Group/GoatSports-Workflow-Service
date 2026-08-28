package iuh.fit.workflowservice.infrastructure.worker;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import iuh.fit.workflowservice.application.dto.request.auth.RegisterVenueOwnerRequest;
import iuh.fit.workflowservice.application.dto.response.auth.RegisteredUserResponse;
import iuh.fit.workflowservice.application.dto.response.base.BaseResponse;
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
    private final CamundaClient camundaClient;

    @JobWorker(type = "registerVenueOwner", autoComplete = false)
    public void handleRegisterVenueOwner(ActivatedJob job) {
        LOGGER.info("Executing registerVenueOwner for job: {}", job.getKey());

        try {
            Map<String, Object> variables = job.getVariablesAsMap();
            RegisterVenueOwnerRequest request = new RegisterVenueOwnerRequest(
                    (String) variables.get("username"),
                    (String) variables.get("fullName"),
                    (String) variables.get("email"),
                    (String) variables.get("password"),
                    (String) variables.get("confirmPassword")
            );

            BaseResponse<RegisteredUserResponse> response = authClient.registerVenueOwner(request);
            if (response.getError() != null || response.getStatusCode() >= 400 || response.getData() == null) {
                throw new ServiceException(String.valueOf(response.getMessage()));
            }

            camundaClient.newCompleteCommand(job.getKey())
                    .variable("userId", response.getData().userId().toString())
                    .send()
                    .join();
        } catch (Exception exception) {
            String message = exception.getMessage() != null
                    ? exception.getMessage()
                    : "Register venue owner failed";
            LOGGER.error("Register venue owner failed for job {}: {}", job.getKey(), message);

            camundaClient.newThrowErrorCommand(job.getKey())
                    .errorCode("REGISTER_VENUE_OWNER_FAILED")
                    .errorMessage(message)
                    .variable("registrationError", message)
                    .send()
                    .join();
        }
    }
}
