package com.flow.workflow.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class WorkflowRequest {
    private String name;
    private List<NodeRequest> nodes;
}
