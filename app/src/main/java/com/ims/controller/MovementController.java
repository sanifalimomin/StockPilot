package com.ims.controller;

import com.ims.model.MovementEvent;
import com.ims.repository.StockMovementLedger;
import com.ims.model.StockMovement;
import com.ims.model.MovementResponse;
import com.ims.model.MovementRequest;
import com.ims.services.MovementPublisher;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/movements")
public class MovementController {

    private final MovementPublisher publisher;
    private final StockMovementLedger ledger;

    public MovementController(MovementPublisher publisher, StockMovementLedger ledger) {
        this.publisher = publisher;
        this.ledger = ledger;
    }

    @PostMapping
    public ResponseEntity<MovementResponse> record(@Valid @RequestBody MovementRequest req) {
        MovementEvent event = new MovementEvent(
                req.sku(), req.warehouseId(), req.type(), req.qty(),
                req.fromWarehouseId(), req.toWarehouseId(), req.idempotencyKey());

        boolean handledSync = publisher.publish(event);

        if (handledSync) {
            StockMovement m = ledger.recent(req.sku(), null, 1).stream()
                    .findFirst().orElse(null);
            MovementResponse body = m != null
                    ? toResponse(m, true)
                    : new MovementResponse(null, req.sku(), req.warehouseId(), req.type(), req.qty(),
                            req.fromWarehouseId(), req.toWarehouseId(), Instant.now(),
                            req.idempotencyKey(), true);
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        }

        MovementResponse body = new MovementResponse(null, req.sku(), req.warehouseId(), req.type(),
                req.qty(), req.fromWarehouseId(), req.toWarehouseId(), Instant.now(),
                req.idempotencyKey(), false);
        return ResponseEntity.accepted().body(body);
    }

    @GetMapping
    public List<MovementResponse> recent(@RequestParam(required = false) String sku,
                                         @RequestParam(required = false) Long warehouseId,
                                         @RequestParam(defaultValue = "50") int limit) {
        return ledger.recent(sku, warehouseId, limit).stream()
                .map(m -> toResponse(m, true))
                .toList();
    }

    private MovementResponse toResponse(StockMovement m, boolean sync) {
        return new MovementResponse(m.getMovementId(), m.getSku(), m.getWarehouseId(), m.getType(),
                m.getQty(), m.getFromWarehouseId(), m.getToWarehouseId(), m.getTimestamp(),
                m.getIdempotencyKey(), sync);
    }
}
