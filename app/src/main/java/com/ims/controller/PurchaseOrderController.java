package com.ims.controller;

import com.ims.model.TransitionRequest;
import com.ims.services.PurchaseOrderService;
import com.ims.model.PurchaseOrderResponse;
import com.ims.model.PurchaseOrderRequest;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService service;

    public PurchaseOrderController(PurchaseOrderService service) {
        this.service = service;
    }

    @GetMapping
    public List<PurchaseOrderResponse> list() {
        return service.findAll().stream().map(PurchaseOrderResponse::from).toList();
    }

    @GetMapping("/{id}")
    public PurchaseOrderResponse get(@PathVariable Long id) {
        return PurchaseOrderResponse.from(service.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseOrderResponse create(@Valid @RequestBody PurchaseOrderRequest req) {
        return PurchaseOrderResponse.from(service.create(req));
    }

    @PostMapping("/{id}/transition")
    public PurchaseOrderResponse transition(@PathVariable Long id,
                                            @Valid @RequestBody TransitionRequest req) {
        return PurchaseOrderResponse.from(service.transition(id, req.status()));
    }
}
