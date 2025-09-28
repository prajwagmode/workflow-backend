package com.flow.workflow.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NodeRequest {
    private String type;
    private String config;   // JSON string
    private Integer orderIndex;
}
