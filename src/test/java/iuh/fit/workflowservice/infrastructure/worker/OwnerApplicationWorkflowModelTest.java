package iuh.fit.workflowservice.infrastructure.worker;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class OwnerApplicationWorkflowModelTest {
    @Test
    void ownerApplicationWorkflowIsValidBpmn() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/venue-owner-registration-process.bpmn")) {
            assertNotNull(stream);
            BpmnModelInstance model = Bpmn.readModelFromStream(stream);
            Bpmn.validateModel(model);
        }
    }
}
