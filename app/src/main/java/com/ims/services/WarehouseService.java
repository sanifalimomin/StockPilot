package com.ims.services;

import com.ims.repository.WarehouseRepository;
import com.ims.model.Warehouse;
import com.ims.utils.NotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class WarehouseService {

    private final WarehouseRepository repo;

    public WarehouseService(WarehouseRepository repo) {
        this.repo = repo;
    }

    public List<Warehouse> findAll() {
        return repo.findAll();
    }

    public Warehouse findById(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Warehouse not found: " + id));
    }

    public Warehouse create(Warehouse w) {
        w.setId(null);
        return repo.save(w);
    }

    public Warehouse update(Long id, Warehouse in) {
        Warehouse w = findById(id);
        w.setCode(in.getCode());
        w.setName(in.getName());
        w.setRegion(in.getRegion());
        return repo.save(w);
    }

    public void delete(Long id) {
        if (!repo.existsById(id)) {
            throw new NotFoundException("Warehouse not found: " + id);
        }
        repo.deleteById(id);
    }
}
