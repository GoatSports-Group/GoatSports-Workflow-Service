package iuh.fit.workflowservice.application.dto.response.venue;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public record OwnerApplicationResponse (
    UUID ownerApplicationId,
    UUID userId,
    String fullName,
    String phone,
    String email,
    String businessName,
    String businessType,
    String taxCode,
    String identityNumber,
    String status,
    String rejectReason,
    LocalDateTime createdAt,
    UUID reviewerId,
    LocalDateTime reviewedAt,

    AddressResponse address,
    Set<DocumentResponse> documents
) {}
