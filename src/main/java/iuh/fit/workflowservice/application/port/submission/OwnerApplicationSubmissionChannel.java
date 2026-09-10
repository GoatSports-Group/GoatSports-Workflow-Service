package iuh.fit.workflowservice.application.port.submission;

import iuh.fit.workflowservice.application.dto.submission.OwnerUploadPreparation;
import iuh.fit.workflowservice.application.exception.ServiceException;

public interface OwnerApplicationSubmissionChannel {
    OwnerUploadPreparation awaitUploadPreparation(long processInstanceKey) throws ServiceException;

    void uploadPreparationReady(OwnerUploadPreparation preparation);

    void uploadPreparationFailed(long processInstanceKey, String message);

    void awaitDocumentsCreated(long processInstanceKey) throws ServiceException;

    void documentsCreated(long processInstanceKey);

    void documentCreationFailed(long processInstanceKey, String message);
}
