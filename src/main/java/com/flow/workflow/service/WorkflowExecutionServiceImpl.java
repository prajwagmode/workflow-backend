package com.flow.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flow.workflow.model.*;
import com.flow.workflow.repository.*;
import com.flow.workflow.executor.NotifyExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.context.annotation.Primary;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
@Primary
@Service
public class WorkflowExecutionServiceImpl implements WorkflowExecutionService {

    @Autowired
    private WorkflowRepository workflowRepo;

    @Autowired
    private WorkflowRunRepository runRepo;

    @Autowired
    private WorkflowRunLogRepository logRepo;

    @Autowired
    private NotifyExecutor notifyExecutor;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // truncate outputs longer than this to avoid huge DB fields / noisy logs
    private static final int MAX_OUTPUT_LENGTH = 5_000;

    @Override
    @Transactional
    public WorkflowRun executeWorkflow(Long workflowId) {
        Workflow workflow = workflowRepo.findById(workflowId)
                .orElseThrow(() -> new RuntimeException("Workflow not found"));

        // Create workflow run entry
        WorkflowRun run = WorkflowRun.builder()
                .workflow(workflow)
                .startedAt(LocalDateTime.now())
                .status(WorkflowStatus.RUNNING)
                .build();
        run = runRepo.saveAndFlush(run);
        System.out.println("Created run id=" + run.getId());

        try {
            // Execute nodes in order
            List<Node> nodes = workflow.getNodes();
            if (nodes != null) {
                nodes.sort(Comparator.comparingInt(Node::getOrderIndex));

                for (Node node : nodes) {
                    System.out.println("Executing node: " + node.getType());

                    // create log entry before execution
                    WorkflowRunLog log = WorkflowRunLog.builder()
                            .run(run)
                            .nodeId(node.getId())
                            .nodeType(node.getType())
                            .status(WorkflowStatus.RUNNING)
                            .startedAt(LocalDateTime.now())
                            .build();
                    log = logRepo.save(log);

                    try {
                        String output = null;
                        switch (node.getType().toUpperCase()) {
                            case "HTTP":
                                output = executeHttpNode(node);
                                log.setStatus(WorkflowStatus.SUCCESS);
                                log.setOutput(compactAndTruncate(output));
                                break;

                            // DELEGATE NOTIFY nodes to NotifyExecutor (it will read latest HTTP output and persist its own run log)
                            case "NOTIFY":
                                // call NotifyExecutor which creates its own log entry (in a REQUIRES_NEW tx)
                                notifyExecutor.executeNotify(run.getId(), node);
                                // mark this execution's log as success — NotifyExecutor also persisted its own detailed log
                                log.setStatus(WorkflowStatus.SUCCESS);
                                log.setOutput("Delegated notify to NotifyExecutor");
                                break;

                            case "CONDITION":
                                output = executeConditionNode(node);
                                log.setStatus(WorkflowStatus.SUCCESS);
                                log.setOutput(compactAndTruncate(output));
                                break;
                            case "DELAY":
                                output = executeDelayNode(node);
                                log.setStatus(WorkflowStatus.SUCCESS);
                                log.setOutput(compactAndTruncate(output));
                                break;
                            default:
                                output = "Unknown node type: " + node.getType();
                                log.setStatus(WorkflowStatus.FAILED);
                                log.setError(output);
                        }
                    } catch (Exception nodeEx) {
                        log.setStatus(WorkflowStatus.FAILED);
                        log.setError(compactAndTruncate(nodeEx == null ? null : nodeEx.getMessage()));
                    } finally {
                        log.setEndedAt(LocalDateTime.now());
                        logRepo.save(log);
                    }
                }
            }

            run.setStatus(WorkflowStatus.SUCCESS);
        } catch (Exception e) {
            run.setStatus(WorkflowStatus.FAILED);
            WorkflowRunLog failureLog = WorkflowRunLog.builder()
                    .run(run)
                    .nodeId(null)
                    .nodeType("SYSTEM")
                    .status(WorkflowStatus.FAILED)
                    .startedAt(LocalDateTime.now())
                    .endedAt(LocalDateTime.now())
                    .error(compactAndTruncate(e == null ? null : e.getMessage()))
                    .build();
            logRepo.save(failureLog);
        } finally {
            run.setEndedAt(LocalDateTime.now());
            runRepo.save(run);

            // update workflow metadata
            workflow.setLastRunAt(run.getEndedAt());
            workflow.setStatus(run.getStatus());
            workflowRepo.save(workflow);
        }

        return run;
    }

    // ---- Node Executors (same as you already have) ----

    private String executeHttpNode(Node node) {
        try {
            JsonNode configJson = objectMapper.readTree(node.getConfig());
            String url = configJson.get("url").asText();
            String method = configJson.has("method")
                    ? configJson.get("method").asText().toUpperCase()
                    : "GET";

            HttpHeaders headers = new HttpHeaders();
            if (configJson.has("headers")) {
                configJson.get("headers").fields().forEachRemaining(entry -> {
                    headers.add(entry.getKey(), entry.getValue().asText());
                });
            }

            String body = configJson.has("body") ? configJson.get("body").toString() : null;
            HttpEntity<String> entity = (body != null) ? new HttpEntity<>(body, headers) : new HttpEntity<>(headers);

            ResponseEntity<String> response;
            if ("POST".equals(method)) {
                response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            } else if ("PUT".equals(method)) {
                response = restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
            } else {
                response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            }
            return response.getBody();
        } catch (Exception e) {
            throw new RuntimeException("HTTP node failed: " + e.getMessage(), e);
        }
    }

    // NOTE: keep executeNotifyNode for testing, but production NOTIFY nodes are handled by NotifyExecutor
    private String executeNotifyNode(Node node) {
        try {
            JsonNode cfg = objectMapper.readTree(node.getConfig());
            String message = cfg.has("message") ? cfg.get("message").asText() : "Default notification";
            String webhookUrl = cfg.has("webhookUrl") ? cfg.get("webhookUrl").asText() : null;

            if (webhookUrl == null || webhookUrl.isBlank()) {
                return "Simulated notify: " + message;
            }

            var payload = objectMapper.createObjectNode().put("text", message);
            String json = objectMapper.writeValueAsString(payload);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(json, headers);

            ResponseEntity<String> resp = restTemplate.exchange(webhookUrl, HttpMethod.POST, entity, String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Slack returned non-2xx: " + resp.getStatusCode());
            }
            return "Slack POST -> status=" + resp.getStatusCodeValue();
        } catch (Exception e) {
            throw new RuntimeException("Notify node failed: " + e.getMessage(), e);
        }
    }

    private String executeConditionNode(Node node) {
        try {
            JsonNode configJson = objectMapper.readTree(node.getConfig());
            String condition = configJson.get("condition").asText();
            String trueMessage = configJson.has("trueMessage") ? configJson.get("trueMessage").asText() : "Condition true";
            String falseMessage = configJson.has("falseMessage") ? configJson.get("falseMessage").asText() : "Condition false";

            if (condition.contains(">")) {
                String[] parts = condition.split(">");
                int left = Integer.parseInt(parts[0].trim());
                int right = Integer.parseInt(parts[1].trim());
                return (left > right) ? trueMessage : falseMessage;
            } else {
                return "Unsupported condition format: " + condition;
            }
        } catch (Exception e) {
            throw new RuntimeException("Condition node failed: " + e.getMessage(), e);
        }
    }

    private String executeDelayNode(Node node) {
        try {
            JsonNode configJson = objectMapper.readTree(node.getConfig());
            int seconds = configJson.get("seconds").asInt();
            Thread.sleep(seconds * 1000L);
            return "Delay of " + seconds + " seconds completed";
        } catch (Exception e) {
            throw new RuntimeException("Delay node failed: " + e.getMessage(), e);
        }
    }

    // compact whitespace (including newlines) and trim, then optionally truncate
    private String compactAndTruncate(String s) {
        if (s == null) return null;
        String compact = s.replaceAll("\\s+", " ").trim();
        if (compact.length() > MAX_OUTPUT_LENGTH) {
            return compact.substring(0, MAX_OUTPUT_LENGTH) + "...[truncated]";
        }
        return compact;
    }
}
