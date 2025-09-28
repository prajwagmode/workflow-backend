package com.flow.workflow.repository;

import com.flow.workflow.model.WorkflowRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowRunRepository extends JpaRepository<WorkflowRun, Long> {
    List<WorkflowRun> findByWorkflowId(Long workflowId);
    Optional<WorkflowRun> findFirstByWorkflowIdOrderByStartedAtDesc(Long workflowId);
    Page<WorkflowRun> findByWorkflowIdOrderByStartedAtDesc(Long workflowId, Pageable pageable);
}
