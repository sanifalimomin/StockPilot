package com.ims.services;

import com.ims.config.ImsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled reorder evaluation. Runs when role is SCHEDULER or ALL.
 * Cron configured via ims.reorder.cron.
 */
@Component
public class ReorderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReorderScheduler.class);

    private final ReorderService reorderService;
    private final boolean active;

    public ReorderScheduler(ReorderService reorderService, ImsProperties props) {
        this.reorderService = reorderService;
        String role = props.getRole().toUpperCase();
        this.active = role.equals("SCHEDULER") || role.equals("ALL");
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
