package com.flow.workflow.controller;

import com.flow.workflow.dto.*;
import com.flow.workflow.model.*;
import com.flow.workflow.repository.*;
import com.flow.workflow.service.WorkflowExecutionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/workflow")
public class WorkflowController {

    @Autowired
    private WorkflowRunRepository runRepo;

    @Autowired
    private WorkflowRepository workflowRepo;

    @Autowired
    private NodeRepository nodeRepo;

    @Autowired
    private WorkflowRunLogRepository logRepo;

    @Autowired
    private WorkflowExecutionService executionService;

    // reuse an ObjectMapper for JSON handling
    private final ObjectMapper objectMapper = new ObjectMapper();

    // -------------------------
    // Create / Publish
    // -------------------------
    @PostMapping("/create")
    public ResponseEntity<Workflow> createWorkflow(@RequestBody WorkflowRequest request) {
        Workflow workflow = new Workflow();
        workflow.setWorkflowName(request.getName());
        workflow.setStatus(WorkflowStatus.DRAFT);

        Workflow savedWorkflow = workflowRepo.save(workflow);

        if (request.getNodes() != null) {
            for (NodeRequest nodeRequest : request.getNodes()) {
                Node node = new Node();
                node.setType(nodeRequest.getType());
                // ensure config is stored as string (your Node model may vary)
                node.setConfig(nodeRequest.getConfig());
                node.setOrderIndex(nodeRequest.getOrderIndex());
                node.setWorkflow(savedWorkflow);
                nodeRepo.save(node);
            }
        }

        // reload workflow with nodes to return a fully populated object
        Workflow full = workflowRepo.findById(savedWorkflow.getId())
                .orElse(savedWorkflow);

        return ResponseEntity.ok(full);
    }

    @PostMapping("/{id}/publish")
    public String publishWorkflow(@PathVariable Long id) {
        Workflow workflow = workflowRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Workflow not found"));
        workflow.setStatus(WorkflowStatus.PUBLISHED);
        workflowRepo.save(workflow);
        return "Workflow published successfully!";
    }

    // -------------------------
    // List / detail (masked by default)
    // -------------------------
    @GetMapping("/all")
    public List<WorkflowResponse> getAllWorkflows(
            @RequestParam(value = "reveal", required = false, defaultValue = "false") boolean reveal) {

        boolean revealSecrets = reveal && isAdmin();
        List<Workflow> all = workflowRepo.findAll();
        return all.stream().map(w -> {
            WorkflowResponse resp = new WorkflowResponse();
            resp.setId(w.getId());
            resp.setWorkflowName(w.getWorkflowName());
            resp.setScheduled(w.getScheduled());
            resp.setLastRunAt(w.getLastRunAt());
            resp.setStatus(w.getStatus());

            List<NodeResponse> nodes = (w.getNodes() == null) ? java.util.List.of() :
                    w.getNodes().stream().map(n -> mapNodeToResponse(n, revealSecrets)).collect(Collectors.toList());
            resp.setNodes(nodes);
            return resp;
        }).collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public WorkflowResponse getWorkflow(
            @PathVariable Long id,
            @RequestParam(value = "reveal", required = false, defaultValue = "false") boolean reveal) {

        boolean revealSecrets = reveal && isAdmin();
        Workflow w = workflowRepo.findById(id).orElseThrow(() -> new RuntimeException("Workflow not found"));
        WorkflowResponse resp = new WorkflowResponse();
        resp.setId(w.getId());
        resp.setWorkflowName(w.getWorkflowName());
        resp.setScheduled(w.getScheduled());
        resp.setLastRunAt(w.getLastRunAt());
        resp.setStatus(w.getStatus());

        List<NodeResponse> nodes = (w.getNodes() == null) ? java.util.List.of() :
                w.getNodes().stream().map(n -> mapNodeToResponse(n, revealSecrets)).collect(Collectors.toList());
        resp.setNodes(nodes);
        return resp;
    }

    // -------------------------
    // Execution endpoints
    // -------------------------
    @PostMapping("/{id}/execute")
    public WorkflowRunResultDto executeWorkflow(@PathVariable Long id) {
        WorkflowRun run = executionService.executeWorkflow(id);

        return new WorkflowRunResultDto(
                run.getId(),
                run.getWorkflow().getId(),
                run.getStatus().name(),
                run.getStartedAt() != null ? run.getStartedAt().toString() : null,
                run.getEndedAt() != null ? run.getEndedAt().toString() : null,
                "Workflow executed with status: " + run.getStatus().name()
        );
    }

    @GetMapping("/{id}/runs")
    public List<WorkflowRun> getWorkflowRuns(@PathVariable Long id) {
        return runRepo.findByWorkflowId(id);
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<String> pauseWorkflow(@PathVariable Long id) {
        Workflow w = workflowRepo.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
        w.setScheduled(false); // stop automatic scheduling
        w.setStatus(WorkflowStatus.DRAFT); // optional: mark "paused"
        workflowRepo.saveAndFlush(w);
        return ResponseEntity.ok("Workflow paused");
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<String> resumeWorkflow(@PathVariable Long id) {
        Workflow w = workflowRepo.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
        w.setScheduled(true);
        workflowRepo.saveAndFlush(w);
        // schedule service will pick it up (see schedule service below)
        return ResponseEntity.ok("Workflow resumed");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteWorkflow(@PathVariable Long id) {
        workflowRepo.findById(id).ifPresent(w -> {
            // optionally cascade delete nodes and runs via JPA cascade config
            workflowRepo.delete(w);
        });
        return ResponseEntity.ok("Workflow deleted (if existed)");
    }

    @PostMapping("/{id}/schedule/enable")
    public ResponseEntity<String> enableSchedule(@PathVariable Long id) {
        Workflow w = workflowRepo.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
        w.setScheduled(true);
        workflowRepo.save(w);
        return ResponseEntity.ok("Scheduling enabled (runs ~every 60s).");
    }

    @PostMapping("/{id}/schedule/disable")
    public String disableSchedule(@PathVariable Long id) {
        Workflow w = workflowRepo.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
        w.setScheduled(false);
        workflowRepo.save(w);
        return "Scheduling disabled.";
    }

    @GetMapping("/{id}/runs/paged")
    public Page<WorkflowRun> getWorkflowRunsPaged(@PathVariable Long id,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        return runRepo.findByWorkflowIdOrderByStartedAtDesc(id, PageRequest.of(page, size));
    }

    @GetMapping("/{id}/runs/latest")
    public ResponseEntity<WorkflowRun> latestRun(@PathVariable Long id) {
        return runRepo.findFirstByWorkflowIdOrderByStartedAtDesc(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // add this method to WorkflowController
    @GetMapping("/runs/{runId}")
    public ResponseEntity<WorkflowRunDto> getRun(@PathVariable Long runId) {
        return runRepo.findById(runId)
                .map(run -> {
                    List<WorkflowRunLogDto> logs = logRepo.findByRun_IdOrderByStartedAtAsc(runId)
                            .stream()
                            .map(l -> new WorkflowRunLogDto(l.getId(), l.getNodeId(), l.getNodeType(),
                                    l.getStatus().name(), l.getOutput(), l.getError(), l.getStartedAt(), l.getEndedAt()))
                            .toList();

                    WorkflowRunDto dto = new WorkflowRunDto(run.getId(), run.getWorkflow().getId(),
                            run.getStatus().name(), run.getStartedAt(), run.getEndedAt(), logs);
                    return ResponseEntity.ok(dto);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // logs for a run
    @GetMapping("/runs/{runId}/logs")
    public List<WorkflowRunLog> getRunLogs(@PathVariable Long runId) {
        return logRepo.findByRun_IdOrderByStartedAtAsc(runId);
    }

    // -------------------------
    // Debug helper (optional)
    // -------------------------
    @GetMapping("/debug/workflows-count")
    public Map<String,Object> debugCounts() {
        Map<String,Object> m = new HashMap<>();
        m.put("repoExists", workflowRepo != null);
        try {
            m.put("count", workflowRepo.count());
            m.put("all", workflowRepo.findAll().stream().map(w -> Map.of("id", w.getId(), "name", w.getWorkflowName())).collect(Collectors.toList()));
        } catch (Exception e) {
            m.put("error", e.toString());
        }
        return m;
    }

    // -------------------------
    // Helpers
    // -------------------------

    /**
     * Map a Node entity to NodeResponse. Always returns a JsonNode in NodeResponse.config.
     */
    private NodeResponse mapNodeToResponse(Node n, boolean revealSecrets) {
        NodeResponse nr = new NodeResponse();
        nr.setId(n.getId());
        nr.setType(n.getType());
        nr.setOrderIndex(n.getOrderIndex());

        // convert whatever Node.getConfig() returns into a JSON string if possible
        String rawCfgStr = null;
        try {
            Object cfgObj = n.getConfig(); // adapt if Node.getConfig() is typed differently
            if (cfgObj == null) {
                rawCfgStr = null;
            } else if (cfgObj instanceof String) {
                rawCfgStr = (String) cfgObj;
            } else {
                // try to serialize other object types (e.g. JsonNode or POJO)
                rawCfgStr = objectMapper.writeValueAsString(cfgObj);
            }
        } catch (Exception e) {
            rawCfgStr = (n.getConfig() == null) ? null : n.getConfig().toString();
        }

        if (revealSecrets) {
            if (rawCfgStr == null) {
                nr.setConfig(null);
            } else {
                try {
                    JsonNode parsed = objectMapper.readTree(rawCfgStr);
                    nr.setConfig(parsed);
                } catch (Exception ex) {
                    // if rawCfgStr is not valid JSON, return as TextNode
                    nr.setConfig(new TextNode(rawCfgStr));
                }
            }
        } else {
            // mask and return — maskConfigJson returns a String (either JSON string or masked text)
            String masked = maskConfigJson(rawCfgStr);
            if (masked == null) {
                nr.setConfig(null);
            } else {
                try {
                    // try parse masked result as JSON
                    JsonNode parsedMasked = objectMapper.readTree(masked);
                    nr.setConfig(parsedMasked);
                } catch (Exception ex) {
                    // not JSON -> wrap into TextNode
                    nr.setConfig(new TextNode(masked));
                }
            }
        }

        return nr;
    }

    /**
     * Mask known sensitive keys inside a JSON config string.
     * If the config is not valid JSON, return a truncated/masked string.
     */
    private String maskConfigJson(String config) {
        if (config == null) return null;
        try {
            JsonNode root = objectMapper.readTree(config);
            String[] sensitive = {"webhookUrl", "webhook", "url", "token", "secret", "apiKey", "authorization", "auth"};

            java.util.function.Consumer<JsonNode> walk = new java.util.function.Consumer<JsonNode>() {
                @Override
                public void accept(JsonNode node) {
                    if (node == null) return;
                    if (node.isObject()) {
                        ObjectNode obj = (ObjectNode) node;
                        for (String key : sensitive) {
                            if (obj.has(key)) {
                                String v = obj.get(key).asText(null);
                                if (v != null && !v.isBlank()) {
                                    String masked = "••••••••";
                                    if (v.length() > 8) masked = "••••••••" + v.substring(v.length() - 8);
                                    obj.put(key, masked);
                                } else {
                                    obj.put(key, "••••••••");
                                }
                            }
                        }
                        // recurse into children
                        obj.fieldNames().forEachRemaining(fn -> accept(obj.get(fn)));
                    } else if (node.isArray()) {
                        for (JsonNode it : node) accept(it);
                    }
                }
            };
            walk.accept(root);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            // not JSON: truncate and hide obvious urls
            String s = config;
            if (s.length() > 200) s = s.substring(0, 200) + "…";
            s = s.replaceAll("(https?://[^\\s\"]+)", "https://••••••••");
            return s;
        }
    }

    /** Basic check for ROLE_ADMIN in the security context. */
    private boolean isAdmin() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) return false;
            return auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ADMIN"));
        } catch (Exception ignored) {
            return false;
        }
    }
}
