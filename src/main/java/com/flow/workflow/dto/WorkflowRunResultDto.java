package com.flow.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WorkflowRunResultDto {
    private Long runId;
    private Long workflowId;
    private String status;
    private String startedAt;
    private String endedAt;
    private String summary; // short execution summary
}
