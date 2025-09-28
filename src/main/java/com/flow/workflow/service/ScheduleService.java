package com.flow.workflow.service;

import com.flow.workflow.model.Workflow;
import com.flow.workflow.repository.WorkflowRepository;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ScheduleService {

    private final ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
    private final Map<Long, ScheduledFuture<?>> jobs = new ConcurrentHashMap<>();

    @Autowired
    private WorkflowRepository workflowRepo;

    @Autowired
    private WorkflowExecutionService executionService;

    // default cron — every minute (0 sec, every minute)
    private static final String DEFAULT_CRON = "0 */5 * * * *";

    @PostConstruct
    public void init() {
        taskScheduler.initialize();
        jobs.clear();
        // optionally schedule existing published workflows initially:
        for (Workflow w : workflowRepo.findAll()) {
            if (Boolean.TRUE.equals(w.getScheduled())) {
                scheduleWorkflow(w);
            }
        }
    }

    public void scheduleWorkflow(Workflow w) {
        if (w == null || w.getId() == null) return;

        // try to read cron from workflow if it exists (use reflection to be safe)
        String cron = DEFAULT_CRON;
        try {
            // safe reflective call — if method absence or exception, we keep default
            var m = w.getClass().getMethod("getCron");
            if (m != null) {
                Object v = m.invoke(w);
                if (v instanceof String && ((String)v).trim().length() > 0) cron = (String) v;
            }
        } catch (NoSuchMethodException ignored) {
            // workflow has no getCron() — use default
        } catch (Exception e) {
            // fallback to default on any other exception
        }

        System.out.println("Scheduling workflow " + w.getId() + " with cron: " + cron);

        // cancel existing job if any
        cancel(w.getId());

        CronTrigger trigger = new CronTrigger(cron);
        ScheduledFuture<?> future = taskScheduler.schedule(() -> {
            try {
                executionService.executeWorkflow(w.getId());
            } catch (Exception e) {
                System.err.println("Scheduled execution failed for workflow " + w.getId() + ": " + e.getMessage());
            }
        }, trigger);

        jobs.put(w.getId(), future);
    }

    public void cancel(Long workflowId) {
        ScheduledFuture<?> f = jobs.remove(workflowId);
        if (f != null) f.cancel(false);
    }
}
