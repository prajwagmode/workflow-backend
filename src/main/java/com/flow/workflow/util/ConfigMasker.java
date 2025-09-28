// src/main/java/com/flow/workflow/util/ConfigMasker.java
package com.flow.workflow.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ConfigMasker {
    private static final ObjectMapper OM = new ObjectMapper();

    public static String maskWebhook(String configJson) {
        if (configJson == null) return null;
        try {
            JsonNode cfg = OM.readTree(configJson);
            if (cfg.isObject()) {
                ObjectNode on = (ObjectNode) cfg;
                if (on.has("webhookUrl")) on.put("webhookUrl", "*****");
                if (on.has("webhook")) on.put("webhook", "*****");
                if (on.has("token")) on.put("token", "*****");
                return OM.writeValueAsString(on);
            }
            return configJson;
        } catch (Exception e) {
            // fallback: strip any http(s) urls (simple)
            return configJson.replaceAll("https?://[^\\s\"']+", "*****");
        }
    }
}
