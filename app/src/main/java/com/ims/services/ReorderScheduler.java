package com.ims.services;

import com.ims.config.ImsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * In-process cron reorder evaluation, active only for role ALL (local demo).
 * In AWS the nightly scan is an EventBridge-triggered one-shot ECS task with
 * role SCHEDULER instead (see SchedulerRunner), so it needs no always-on task.
 * Cron configured via ims.reorder.cron.
 */
@Component
public class ReorderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReorderScheduler.class);

    private final ReorderService reorderService;
    private final boolean active;

    public ReorderScheduler(ReorderService reorderService, ImsProperties props) {
        this.reorderService = reorderService;
        this.active = props.getRole().equalsIgnoreCase("ALL");
    }

    @Scheduled(cron = "${ims.reorder.cron}")
    public void run() {
        if (!active) {
            return;
        }
        log.info("Running scheduled reorder evaluation");
        reorderService.scanAll();
    }
}
