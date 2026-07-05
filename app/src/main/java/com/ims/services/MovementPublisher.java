package com.ims.services;

import com.ims.model.MovementEvent;

public interface MovementPublisher {

    boolean publish(MovementEvent event);
}
