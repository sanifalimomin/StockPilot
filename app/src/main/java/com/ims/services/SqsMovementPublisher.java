package com.ims.services;

import com.ims.model.MovementEvent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.config.ImsProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/** Publishes movement events to SQS for the worker role (prod). */
@Component
@ConditionalOnProperty(name = "ims.aws.sqs.enabled", havingValue = "true", matchIfMissing = true)
public class SqsMovementPublisher implements MovementPublisher {

    private final SqsClient sqs;
    private final ObjectMapper mapper;
    private final String queueUrl;

    public SqsMovementPublisher(SqsClient sqs, ObjectMapper mapper, ImsProperties props) {
        this.sqs = sqs;
        this.mapper = mapper;
        this.queueUrl = props.getAws().getSqs().getQueueUrl();
    }

    @Override
    public boolean publish(MovementEvent event) {
        try {
            String body = mapper.writeValueAsString(event);
            SendMessageRequest.Builder req = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(body);
            sqs.sendMessage(req.build());
            return false; // asynchronous
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize movement event", e);
        }
    }
}
