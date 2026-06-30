package com.ims.services;

import com.ims.model.MovementEvent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.config.ImsProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SQS long-poll worker. Active only when role is WORKER or ALL and SQS is enabled.
 */
@Component
@ConditionalOnProperty(name = "ims.aws.sqs.enabled", havingValue = "true", matchIfMissing = true)
public class SqsMovementWorker {

    private static final Logger log = LoggerFactory.getLogger(SqsMovementWorker.class);

    private final SqsClient sqs;
    private final ObjectMapper mapper;
    private final MovementProcessor processor;
    private final ImsProperties props;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executor;

    public SqsMovementWorker(SqsClient sqs, ObjectMapper mapper,
                             MovementProcessor processor, ImsProperties props) {
        this.sqs = sqs;
        this.mapper = mapper;
        this.processor = processor;
        this.props = props;
    }

    @PostConstruct
    void start() {
        String role = props.getRole().toUpperCase();
        if (!role.equals("WORKER") && !role.equals("ALL")) {
            log.info("SQS worker disabled for role={}", role);
            return;
        }
        running.set(true);
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "sqs-movement-worker");
            t.setDaemon(true);
            return t;
        });
        executor.submit(this::poll);
        log.info("SQS movement worker started for queue {}", props.getAws().getSqs().getQueueUrl());
    }

    private void poll() {
        String queueUrl = props.getAws().getSqs().getQueueUrl();
        while (running.get()) {
            try {
                var receive = ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(10)
                        .waitTimeSeconds(20)
                        .build();
                for (Message msg : sqs.receiveMessage(receive).messages()) {
                    handle(queueUrl, msg);
                }
            } catch (Exception e) {
                if (running.get()) {
                    log.error("Error polling SQS, backing off", e);
                    sleep(2000);
                }
            }
        }
    }

    private void handle(String queueUrl, Message msg) {
        try {
            MovementEvent event = mapper.readValue(msg.body(), MovementEvent.class);
            processor.process(event);
            sqs.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(msg.receiptHandle())
                    .build());
        } catch (Exception e) {
            // leave message on queue for redelivery / DLQ
            log.error("Failed to process movement message {}", msg.messageId(), e);
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    @PreDestroy
    void stop() {
        running.set(false);
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
