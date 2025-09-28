package com.flow.workflow.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "workflow_run_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowRunLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // owning run
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id")
    private WorkflowRun run;

    private Long nodeId;
    private String nodeType;

    @Enumerated(EnumType.STRING)
    private WorkflowStatus status;

    @Column(columnDefinition = "TEXT")
    private String output;

    @Column(columnDefinition = "TEXT")
    private String error;

    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}
