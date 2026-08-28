package iuh.fit.workflowservice.application.dto.request.auth;

public record RegisterVenueOwnerRequest(
        String username,
        String fullName,
        String email,
        String password,
        String confirmPassword
) {
}
