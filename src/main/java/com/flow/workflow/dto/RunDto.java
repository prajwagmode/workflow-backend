package com.flow.workflow.dto;

import java.time.LocalDateTime;

public class RunDto {
    private Long id;
    private Long workflowId;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private String status;

    // constructors/getters/setters
    public RunDto() {}
    public RunDto(Long id, Long workflowId, LocalDateTime startedAt, LocalDateTime endedAt, String status) {
        this.id = id;
        this.workflowId = workflowId;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.status = status;
    }
    // getters & setters...
}
