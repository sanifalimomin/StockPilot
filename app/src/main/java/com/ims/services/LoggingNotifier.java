package com.ims.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "ims.aws.sns.enabled", havingValue = "false")
public class LoggingNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotifier.class);

    @Override
    public void notifyLowStock(String subject, String message) {
        log.warn("[LOW-STOCK NOTIFICATION] {} :: {}", subject, message);
    }
}
