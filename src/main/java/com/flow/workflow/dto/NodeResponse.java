package com.flow.workflow.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NodeResponse {
    private Long id;
    private String type;
    private Integer orderIndex;
    /**
     * Masked config. Use Object so controller can return either JSON (JsonNode) or String
     * depending on whether the config is valid JSON and whether secrets are revealed.
     */
    private JsonNode config;
}
