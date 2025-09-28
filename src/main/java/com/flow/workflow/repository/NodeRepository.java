package com.flow.workflow.repository;

import com.flow.workflow.model.Node;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NodeRepository extends JpaRepository<Node, Long> {
    List<Node> findByWorkflowIdOrderByOrderIndex(Long workflowId);
}
