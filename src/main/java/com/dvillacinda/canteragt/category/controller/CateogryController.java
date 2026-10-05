package com.dvillacinda.canteragt.category.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dvillacinda.canteragt.category.dto.CategoryCreateRequest;
import com.dvillacinda.canteragt.category.dto.CategoryResponse;
import com.dvillacinda.canteragt.category.dto.CategoryUpdateRequest;
import com.dvillacinda.canteragt.shared.dto.ApiResponse;
import com.dvillacinda.canteragt.shared.enums.Status;
import com.dvillacinda.canteragt.category.service.CategoryService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/category")
@PreAuthorize("hasRole('ACADEMY_ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class CateogryController {

    private final CategoryService categoryService;

    @PostMapping("/create-category")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @Valid @RequestBody CategoryCreateRequest request) {
        log.info("Creating coach");
        CategoryResponse response = categoryService.createCategory(request);
        log.info("Coach created with id {}", response.categoryId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, HttpStatus.CREATED, "Coach created successfully", response));
    }

    @PatchMapping("/update-category-by-id/{categoryId}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategoryById(@PathVariable UUID categoryId,
            @Valid @RequestBody CategoryUpdateRequest request) {
        log.info("Updating coach with id {}", categoryId);
        CategoryResponse response = categoryService.updateCategory(categoryId, request);
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Coach updated successfully", response));
    }

    @GetMapping("/get-category-by-id/{categoryId}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategoryById(@PathVariable UUID categoryId) {
        log.info("Getting coach with id {}", categoryId);
        CategoryResponse response = categoryService.getCategoryById(categoryId);
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Coach found successfully", response));
    }

    @DeleteMapping("/delete-category-by-id/{categoryId}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCategoryById(@PathVariable UUID categoryId) {
        log.info("Deleting coach with id {}", categoryId);
        categoryService.deleteCategoryById(categoryId);
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Coach deleted successfully", null));
    }

    @PatchMapping("/update-category-status-by-id/{categoryId}/{status}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategoryStatusById(@PathVariable UUID categoryId,
            @PathVariable Status status) {
        log.info("Updating coach status with id {}", categoryId);
        CategoryResponse response = categoryService.updateCategoryStatusById(categoryId, status);
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Coach status updated successfully", response));
    }

}
