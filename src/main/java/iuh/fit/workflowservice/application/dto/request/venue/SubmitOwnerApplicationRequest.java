package iuh.fit.workflowservice.application.dto.request.venue;

public record SubmitOwnerApplicationRequest(
    String userId,
    String fullName,
    String phone,
    String email,
    String identityNumber,
    String businessName,
    String businessType,
    String taxCode,
    String address,
    String province,
    String district,
    String ward,
    String city,
    Double latitude,
    Double longitude
){}
