package com.flow.workflow.controller;

import com.flow.workflow.model.Workflow;
import com.flow.workflow.repository.WorkflowRepository;
import com.flow.workflow.service.WorkflowExecutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhook")
public class WebhookController {

    @Autowired
    private WorkflowRepository workflowRepo;

    @Autowired
    private WorkflowExecutionService executionService;

    /**
     * Simple webhook trigger: POST /webhook/{workflowId}?secret=xxx
     * (You should validate secret from workflow config in prod)
     */
    @PostMapping("/{workflowId}")
    public ResponseEntity<String> trigger(@PathVariable Long workflowId,
                                          @RequestParam(value = "secret", required = false) String secret) {
        Workflow w = workflowRepo.findById(workflowId).orElse(null);
        if (w == null) return ResponseEntity.notFound().build();

        // optional: validate secret against stored workflow.secret (if present)
        // For now just start execution in background thread and return 202
        new Thread(() -> {
            try {
                executionService.executeWorkflow(workflowId);
            } catch (Exception e) {
                // log if you have logger
                System.err.println("Webhook triggered workflow failed: " + e.getMessage());
            }
        }).start();

        return ResponseEntity.accepted().body("Triggered");
    }
}
