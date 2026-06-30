package com.ims.services;

import com.ims.model.MovementEvent;

/**
 * Port for enqueuing movement events. In prod this publishes to SQS for the worker
 * role; in local (SQS disabled) the inline implementation processes synchronously.
 */
public interface MovementPublisher {

    /**
     * Publish a movement event for processing.
     *
     * @return true if the event was handled synchronously (caller can read fresh state),
     * false if it was enqueued for asynchronous processing.
     */
    boolean publish(MovementEvent event);
}
