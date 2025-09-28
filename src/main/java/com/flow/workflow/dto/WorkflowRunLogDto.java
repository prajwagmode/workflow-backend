package com.flow.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
public class WorkflowRunLogDto {
    private Long id;
    private Long nodeId;
    private String nodeType;
    private String status;
    private String output;
    private String error;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}
