package iuh.fit.workflowservice.presentation.controller;

import iuh.fit.workflowservice.application.exception.ServiceException;
import iuh.fit.workflowservice.application.port.security.CurrentUserProvider;
import iuh.fit.workflowservice.application.service.OwnerApplicationProgressService;
import iuh.fit.workflowservice.application.util.BaseUtil;
import iuh.fit.workflowservice.presentation.request.OwnerApplicationProgressSearchRequest;
import iuh.fit.workflowservice.presentation.response.OwnerApplicationProgressSearchResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workflows/owner-applications")
@RequiredArgsConstructor
public class OwnerApplicationProgressController {
    private final OwnerApplicationProgressService progressService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/my/progress/search")
    public ResponseEntity<OwnerApplicationProgressSearchResponse> findMyProgress(
            @Valid @RequestBody OwnerApplicationProgressSearchRequest request
    ) throws ServiceException {
        UUID ownerId = currentUserId();
        return ResponseEntity.ok(new OwnerApplicationProgressSearchResponse(
                progressService.findMyProgress(request.ownerApplicationIds(), ownerId)
        ));
    }

    @PutMapping("/{ownerApplicationId}/viewed")
    @PreAuthorize("hasAuthority('GET_OWNER_APPLICATION')")
    public ResponseEntity<Void> markViewed(
            @PathVariable UUID ownerApplicationId
    ) throws ServiceException {
        progressService.markViewed(ownerApplicationId, currentUserId());
        return ResponseEntity.noContent().build();
    }

    private UUID currentUserId() throws ServiceException {
        String userId = currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new ServiceException("Bạn phải đăng nhập để thực hiện chức năng này."));
        return BaseUtil.uuid(userId);
    }
}
