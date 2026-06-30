package com.ims.config;

import com.ims.services.ReorderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Local-demo helper: logs startup info and runs an initial reorder scan so the
 * /alerts endpoint has data immediately after boot.
 */
@Component
@Profile("local")
public class DemoRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoRunner.class);

    private final ImsProperties props;
    private final ReorderService reorderService;

    public DemoRunner(ImsProperties props, ReorderService reorderService) {
        this.props = props;
        this.reorderService = reorderService;
    }

    @Override
    public void run(String... args) {
        log.info("=== IMS LOCAL DEMO === role={} aws.enabled={} forecast={}",
                props.getRole(), props.getAws().isEnabled(), props.getForecast().getProvider());
        int alerts = reorderService.scanAll();
        log.info("Initial reorder scan raised/found {} open low-stock alert(s)", alerts);
        log.info("API base: http://localhost:8080/api/v1  (try GET /health)");
    }
}
