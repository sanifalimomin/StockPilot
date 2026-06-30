package com.ims.controller;

import com.ims.model.CategoryRequest;
import com.ims.model.Category;
import com.ims.services.CategoryService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.config.ImsProperties;
import com.ims.utils.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CategoryController.class)
@Import({GlobalExceptionHandler.class, ImsProperties.class})
class CategoryControllerTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @MockBean
    CategoryService service;

    @Test
    void listReturnsCategories() throws Exception {
        Category c = new Category();
        c.setId(1L);
        c.setName("Electronics");
        when(service.findAll()).thenReturn(List.of(c));

        mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Electronics"));
    }

    @Test
    void createValidatesBlankName() throws Exception {
        CategoryRequest bad = new CategoryRequest("", null);
        mvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/v1/categories"));
    }

    @Test
    void getMissingReturns404() throws Exception {
        when(service.findById(eq(99L))).thenThrow(new NotFoundException("Category not found: 99"));
        mvc.perform(get("/api/v1/categories/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void createReturns201() throws Exception {
        Category saved = new Category();
        saved.setId(5L);
        saved.setName("Tools");
        when(service.create(any())).thenReturn(saved);

        mvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new CategoryRequest("Tools", "desc"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5));
    }
}
