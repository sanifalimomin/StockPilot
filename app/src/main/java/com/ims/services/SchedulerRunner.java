package com.ims.services;

import com.ims.config.ImsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * One-shot scheduler entrypoint for the EventBridge-triggered ECS task.
 * When role is SCHEDULER the app boots, runs a single reorder scan, and exits
 * so the RunTask invocation terminates (unlike the long-lived API/worker
 * services). Role ALL keeps the in-process cron instead (see ReorderScheduler).
 */
@Component
public class SchedulerRunner {

    private static final Logger log = LoggerFactory.getLogger(SchedulerRunner.class);

    private final ReorderService reorderService;
    private final ReportService reportService;
    private final ImsProperties props;
    private final ConfigurableApplicationContext context;

    public SchedulerRunner(ReorderService reorderService,
                           ReportService reportService,
                           ImsProperties props,
                           ConfigurableApplicationContext context) {
        this.reorderService = reorderService;
        this.reportService = reportService;
        this.props = props;
        this.context = context;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void runOnceAndExit() {
        if (!"SCHEDULER".equalsIgnoreCase(props.getRole())) {
            return;
        }
        int exitCode = 0;
        try {
            int openAlerts = reorderService.scanAll();
            log.info("One-shot reorder scan complete; {} open low-stock alert(s)", openAlerts);
        } catch (Exception e) {
            log.error("One-shot reorder scan failed", e);
            exitCode = 1; // non-zero so the ECS task reports failure
        }
        try {
            reportService.generateDailyReports()
                    .forEach(r -> log.info("Nightly report stored: {} -> {}", r.get("filename"), r.get("location")));
        } catch (Exception e) {
            log.error("Nightly report generation failed", e);
            exitCode = 1;
        }
        final int code = exitCode;
        // Shut down off the event thread so context close doesn't deadlock startup.
        Thread exiter = new Thread(() -> System.exit(SpringApplication.exit(context, () -> code)),
                "scheduler-exit");
        exiter.setDaemon(false);
        exiter.start();
    }
}
