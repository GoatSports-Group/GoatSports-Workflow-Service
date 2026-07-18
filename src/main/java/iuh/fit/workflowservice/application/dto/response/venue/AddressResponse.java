package iuh.fit.workflowservice.application.dto.response.venue;

import java.util.UUID;

public record AddressResponse(
    UUID addressId,
    String address,
    String ward,
    String district,
    String city,
    String province
) {
}