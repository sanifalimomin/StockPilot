package com.ims.services;

import com.ims.repository.SupplierRepository;
import com.ims.model.Supplier;
import com.ims.utils.NotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class SupplierService {

    private final SupplierRepository repo;

    public SupplierService(SupplierRepository repo) {
        this.repo = repo;
    }

    public List<Supplier> findAll() {
        return repo.findAll();
    }

    public Supplier findById(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Supplier not found: " + id));
    }

    public Supplier create(Supplier s) {
        s.setId(null);
        return repo.save(s);
    }

    public Supplier update(Long id, Supplier in) {
        Supplier s = findById(id);
        s.setName(in.getName());
        s.setEmail(in.getEmail());
        s.setPhone(in.getPhone());
        return repo.save(s);
    }

    public void delete(Long id) {
        if (!repo.existsById(id)) {
            throw new NotFoundException("Supplier not found: " + id);
        }
        repo.deleteById(id);
    }
}
