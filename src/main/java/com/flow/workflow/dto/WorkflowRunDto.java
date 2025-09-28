package com.flow.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class WorkflowRunDto {
    private Long id;
    private Long workflowId;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private List<WorkflowRunLogDto> logs;
}
