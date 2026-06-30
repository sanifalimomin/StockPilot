package com.ims.services;

import com.ims.repository.CategoryRepository;
import com.ims.model.Category;
import com.ims.utils.NotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class CategoryService {

    private final CategoryRepository repo;

    public CategoryService(CategoryRepository repo) {
        this.repo = repo;
    }

    public List<Category> findAll() {
        return repo.findAll();
    }

    public Category findById(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Category not found: " + id));
    }

    public Category create(Category c) {
        c.setId(null);
        return repo.save(c);
    }

    public Category update(Long id, Category in) {
        Category c = findById(id);
        c.setName(in.getName());
        c.setDescription(in.getDescription());
        return repo.save(c);
    }

    public void delete(Long id) {
        if (!repo.existsById(id)) {
            throw new NotFoundException("Category not found: " + id);
        }
        repo.deleteById(id);
    }
}
