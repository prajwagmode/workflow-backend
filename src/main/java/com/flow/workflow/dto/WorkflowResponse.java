package com.flow.workflow.dto;

import com.flow.workflow.model.WorkflowStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class WorkflowResponse {
    private Long id;
    private String workflowName;
    private Boolean scheduled;
    private LocalDateTime lastRunAt;
    private WorkflowStatus status;
    private List<NodeResponse> nodes;
}
