package com.flow.workflow.repository;

import com.flow.workflow.model.Workflow;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface WorkflowRepository extends JpaRepository<Workflow, Long> {
    Optional<Workflow> findByWorkflowName(String name);
}
