package com.flow.workflow.service;

import com.flow.workflow.model.WorkflowRun;

public interface WorkflowExecutionService {
    /**
     * Execute the workflow and return the persisted WorkflowRun instance.
     */
    WorkflowRun executeWorkflow(Long workflowId);
}
