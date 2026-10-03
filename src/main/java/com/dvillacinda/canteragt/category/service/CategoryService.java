package com.dvillacinda.canteragt.category.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dvillacinda.canteragt.category.dto.CategoryCreateRequest;
import com.dvillacinda.canteragt.category.dto.CategoryResponse;
import com.dvillacinda.canteragt.category.dto.CategoryUpdateRequest;
import com.dvillacinda.canteragt.category.mapper.CategoryMapper;
import com.dvillacinda.canteragt.category.repository.CategoryRepository;
import com.dvillacinda.canteragt.shared.enums.Status;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    public CategoryResponse createCategory(CategoryCreateRequest request) {
        var category = categoryRepository.save(categoryMapper.toEntity(request));
        return categoryMapper.toResponse(category);
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(UUID categoryId) {
        var category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Category with id " + categoryId + " not found"));
        return categoryMapper.toResponse(category);
    }

    public void deleteCategoryById(UUID categoryId) {
        var category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Category with id " + categoryId + " not found"));
        categoryRepository.delete(category);
    }

    public CategoryResponse updateCategory(UUID categoryId, CategoryUpdateRequest request) {
        var exist = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Category with id " + categoryId + " not found"));

        if (request.name() != null)
            exist.setName(request.name());
        if (request.minAge() != null)
            exist.setMinAge(request.minAge());
        if (request.maxAge() != null)
            exist.setMaxAge(request.maxAge());
        if (request.categoryCode() != null)
            exist.setCategoryCode(request.categoryCode());
        return categoryMapper.toResponse(categoryRepository.save(exist));
    }

    public CategoryResponse updateCategoryStatusById(UUID categoryId, Status status) {
        var exist = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Category with id " + categoryId + " not found"));
        exist.setStatus(status);
        return categoryMapper.toResponse(categoryRepository.save(exist));
    }
}
