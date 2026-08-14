package iuh.fit.workflowservice.presentation.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record OwnerApplicationProgressSearchRequest(
        @NotEmpty @Size(max = 20) List<@NotNull UUID> ownerApplicationIds
) { }
