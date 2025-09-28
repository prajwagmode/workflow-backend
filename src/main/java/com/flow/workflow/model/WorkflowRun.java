package com.flow.workflow.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
// Map to the existing DB table name (singular) so FK relationships match the DB
@Table(name = "workflow_run")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // link back to workflow
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id")
    private Workflow workflow;

    @Enumerated(EnumType.STRING)
    private WorkflowStatus status;

    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    // optional convenience - keeps in-sync JPA mapping (not strictly required)
    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WorkflowRunLog> logs;
}
