package com.ims.services;

import com.ims.repository.ProductRepository;
import com.ims.model.Product;
import com.ims.utils.BusinessException;
import com.ims.utils.NotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@Transactional
public class ProductService {

    private final ProductRepository repo;

    public ProductService(ProductRepository repo) {
        this.repo = repo;
    }

    public List<Product> search(Long categoryId, Long supplierId, String q) {
        String query = StringUtils.hasText(q) ? q : null;
        return repo.search(categoryId, supplierId, query);
    }

    public Product findById(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found: " + id));
    }

    public Product create(Product p) {
        p.setId(null);
        if (repo.existsBySku(p.getSku())) {
            throw new BusinessException("SKU already exists: " + p.getSku());
        }
        return repo.save(p);
    }

    public Product update(Long id, Product in) {
        Product p = findById(id);
        p.setSku(in.getSku());
        p.setName(in.getName());
        p.setCategoryId(in.getCategoryId());
        p.setSupplierId(in.getSupplierId());
        p.setUnitCost(in.getUnitCost());
        p.setReorderPoint(in.getReorderPoint());
        p.setReorderQty(in.getReorderQty());
        return repo.save(p);
    }

    public void delete(Long id) {
        if (!repo.existsById(id)) {
            throw new NotFoundException("Product not found: " + id);
        }
        repo.deleteById(id);
    }
}
