package com.flow.workflow.model;

/**
 * Basic workflow/run statuses used across the app.
 * Add/remove statuses as your service expects.
 */
public enum WorkflowStatus {
    DRAFT,
    PUBLISHED,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELED
}
