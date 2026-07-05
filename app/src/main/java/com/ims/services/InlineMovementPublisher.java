package com.ims.services;

import com.ims.model.MovementEvent;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "ims.aws.sqs.enabled", havingValue = "false")
public class InlineMovementPublisher implements MovementPublisher {

    private final MovementProcessor processor;

    public InlineMovementPublisher(MovementProcessor processor) {
        this.processor = processor;
    }

    @Override
    public boolean publish(MovementEvent event) {
        processor.process(event);
        return true;
    }
}
