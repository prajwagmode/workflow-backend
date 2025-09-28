package com.flow.workflow.repository;

import com.flow.workflow.model.WorkflowRunLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowRunLogRepository extends JpaRepository<WorkflowRunLog, Long> {
    List<WorkflowRunLog> findByRun_IdOrderByStartedAtAsc(Long runId);
    Optional<WorkflowRunLog> findFirstByRun_IdAndNodeTypeOrderByIdDesc(Long runId, String nodeType);
}
