package com.flow.workflow.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flow.workflow.model.WorkflowRun;
import com.flow.workflow.model.WorkflowRunLog;
import com.flow.workflow.model.WorkflowStatus;
import com.flow.workflow.model.Node;
import com.flow.workflow.repository.WorkflowRunLogRepository;
import com.flow.workflow.repository.WorkflowRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class NotifyExecutor {

    private final Logger log = LoggerFactory.getLogger(NotifyExecutor.class);
    private final WorkflowRunLogRepository runLogRepository;
    private final WorkflowRunRepository runRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // truncate outputs longer than this to avoid huge DB fields / noisy logs
    private static final int MAX_OUTPUT_LENGTH = 5_000;

    public NotifyExecutor(WorkflowRunLogRepository runLogRepository,
                          WorkflowRunRepository runRepository) {
        this.runLogRepository = runLogRepository;
        this.runRepository = runRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Execute notify node for the given runId & node config.
     * We persist a runLog entry in a new transaction so it won't hold locks for the whole execution flow.
     */
    public void executeNotify(Long runId, Node node) {
        // configured fallback message from the node config (if any)
        String configuredMessage = getConfiguredMessage(node);

        // build message from latest HTTP output if available
        String messageFromHttp = buildMessageFromHttpOutput(runId);

        // If HTTP produced a valid weather summary, use it.
        // Otherwise fall back to configuredMessage if present, else keep the HTTP fallback text.
        String message;
        if (messageFromHttp != null && !isHttpFallback(messageFromHttp)) {
            message = messageFromHttp;
        } else if (configuredMessage != null && !configuredMessage.isBlank()) {
            message = configuredMessage;
        } else {
            message = messageFromHttp; // e.g. "Weather report: (no HTTP data available)"
        }

        // read webhook from node config
        String webhookUrl = parseWebhookUrl(node);
        if (webhookUrl == null || webhookUrl.isBlank()) {
            String err = "Missing webhookUrl in node config";
            log.error(err);
            persistRunLog(runId, node.getId(), "NOTIFY", null, WorkflowStatus.FAILED, err);
            return;
        }

        // try sending to Slack
        try {
            postSlackWebhook(webhookUrl, message);
            persistRunLog(runId, node.getId(), "NOTIFY", message, WorkflowStatus.SUCCESS, null);
            log.info("Notify sent successfully for runId {} message='{}'", runId, compactForLog(message));
        } catch (Exception ex) {
            String err = ex.getMessage();
            log.error("Notify node failed for runId " + runId, ex);
            persistRunLog(runId, node.getId(), "NOTIFY", message, WorkflowStatus.FAILED, err);
        }
    }

    /**
     * Return configured message from node config JSON if present.
     */
    private String getConfiguredMessage(Node node) {
        try {
            if (node == null || node.getConfig() == null) return null;
            JsonNode cfg = objectMapper.readTree(node.getConfig());
            if (cfg.has("message")) {
                return cfg.get("message").asText(null);
            }
            // some configs might use 'text' or 'body' as key
            if (cfg.has("text")) {
                return cfg.get("text").asText(null);
            }
            if (cfg.has("body")) {
                return cfg.get("body").asText(null);
            }
            return null;
        } catch (Exception e) {
            log.debug("Unable to read configured message from node config", e);
            return null;
        }
    }

    private String parseWebhookUrl(Node node) {
        // node.getConfig() may be JSON string; adapt to your model. Example assumes config is JSON string.
        try {
            if (node == null || node.getConfig() == null) return null;
            JsonNode cfg = objectMapper.readTree(node.getConfig());
            JsonNode w = cfg.path("webhookUrl");
            if (!w.isMissingNode() && !w.asText().isBlank()) return w.asText();
            // older config might use "webhook" or "url" — try fallbacks:
            JsonNode w2 = cfg.path("webhook");
            if (!w2.isMissingNode() && !w2.asText().isBlank()) return w2.asText();
            JsonNode w3 = cfg.path("url");
            if (!w3.isMissingNode() && !w3.asText().isBlank()) return w3.asText();
            return null;
        } catch (Exception e) {
            log.warn("Failed to parse node config for webhookUrl", e);
            return null;
        }
    }

    /**
     * Build a weather message from the latest HTTP run log for the runId.
     * Returns one of:
     *   - "Daily weather — Temp: 22.5°C · Wind: 7.0 km/h · code:80"  (good)
     *   - "Weather report: (no HTTP data available)"               (fallback)
     *   - "Weather report: (failed to parse HTTP output)"          (parse error)
     */
    private String buildMessageFromHttpOutput(Long runId) {
        try {
            Optional<WorkflowRunLog> maybe = runLogRepository.findFirstByRun_IdAndNodeTypeOrderByIdDesc(runId, "HTTP");
            if (maybe.isEmpty()) {
                return "Weather report: (no HTTP data available)";
            }
            String output = maybe.get().getOutput();
            if (output == null || output.isBlank()) return "Weather report: (empty HTTP output)";

            JsonNode root = objectMapper.readTree(output);

            // First try Open-Meteo style "current_weather"
            JsonNode current = root.path("current_weather");
            String temp = current.path("temperature").asText(null);
            String wind = current.path("windspeed").asText(null);
            String code = current.path("weathercode").asText(null);

            // fallback for other field names (some responses use /current/temperature)
            if (temp == null || temp.equals("null")) {
                JsonNode maybeTemp = root.at("/current/temperature");
                if (!maybeTemp.isMissingNode()) temp = maybeTemp.asText(null);
            }

            // If we have at least one meaningful weather field, build summary
            boolean hasWeather = (temp != null && !temp.equals("null")) ||
                    (wind != null && !wind.equals("null")) ||
                    (code != null && !code.equals("null"));

            if (hasWeather) {
                StringBuilder summary = new StringBuilder();
                summary.append("Daily weather — ");
                if (temp != null && !"null".equals(temp)) summary.append("Temp: ").append(temp).append("°C");
                else summary.append("Temp: N/A");

                if (wind != null && !"null".equals(wind)) summary.append(" · Wind: ").append(wind).append(" km/h");
                if (code != null && !"null".equals(code)) summary.append(" · code:").append(code);

                return summary.toString();
            }

            // No recognizable weather fields found
            return "Weather report: (no HTTP data available)";
        } catch (Exception e) {
            log.warn("Failed to parse HTTP output JSON for runId {}: {}", runId, e.getMessage());
            return "Weather report: (failed to parse HTTP output)";
        }
    }

    private boolean isHttpFallback(String messageFromHttp) {
        if (messageFromHttp == null) return true;
        String m = messageFromHttp.toLowerCase();
        return m.contains("no http data") || m.contains("empty http") || m.contains("failed to parse");
    }

    private void postSlackWebhook(String webhookUrl, String message) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("text", message);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> resp = restTemplate.postForEntity(webhookUrl, entity, String.class);
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Slack webhook returned " + resp.getStatusCode());
        }
    }

    // persist run log in new transaction (so we don't hold locks across node execution)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void persistRunLog(Long runId, Long nodeId, String nodeType, String output, WorkflowStatus status, String error) {
        WorkflowRunLog l = new WorkflowRunLog();
        // set run object from runId
        WorkflowRun run = runRepository.findById(runId).orElse(null);
        l.setRun(run);
        l.setNodeId(nodeId);
        l.setNodeType(nodeType);

        // compact and truncate output to single-line
        if (output != null) {
            String compact = compact(output);
            if (compact.length() > MAX_OUTPUT_LENGTH) {
                compact = compact.substring(0, MAX_OUTPUT_LENGTH) + "...[truncated]";
            }
            l.setOutput(compact);
        } else {
            l.setOutput(null);
        }

        l.setStatus(status);
        l.setError(error);
        l.setStartedAt(LocalDateTime.now());
        l.setEndedAt(LocalDateTime.now());
        runLogRepository.save(l);
    }

    // compact helper used only here
    private String compact(String s) {
        if (s == null) return null;
        // replace consecutive whitespace (including newlines) with single space and trim
        return s.replaceAll("\\s+", " ").trim();
    }

    // small helper for log messages to avoid huge output in logs
    private String compactForLog(String s) {
        if (s == null) return null;
        String c = compact(s);
        return c.length() > 200 ? c.substring(0, 200) + "...[truncated]" : c;
    }
}
