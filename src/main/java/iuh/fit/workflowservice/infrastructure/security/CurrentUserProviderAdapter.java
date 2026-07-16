package iuh.fit.workflowservice.infrastructure.security;

import iuh.fit.workflowservice.application.port.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CurrentUserProviderAdapter implements CurrentUserProvider {
    private final SecurityContextHelper securityContextHelper;

    @Override
    public Optional<String> getCurrentUserId() {
        return securityContextHelper.getCurrentUserId();
    }
}
