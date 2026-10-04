package com.dvillacinda.canteragt.academy_category.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dvillacinda.canteragt.academy.mapper.AcademyMapper;
import com.dvillacinda.canteragt.academy.service.AcademyService;
import com.dvillacinda.canteragt.academy_category.dto.AcademyCategoryCreateRequest;
import com.dvillacinda.canteragt.academy_category.dto.AcademyCategoryResponse;
import com.dvillacinda.canteragt.academy_category.mapper.AcademyCategoryMapper;
import com.dvillacinda.canteragt.academy_category.repository.AcademyCategoryRepository;
import com.dvillacinda.canteragt.category.mapper.CategoryMapper;
import com.dvillacinda.canteragt.category.service.CategoryService;
import com.dvillacinda.canteragt.season.mapper.SeasonMapper;
import com.dvillacinda.canteragt.season.service.SeasonService;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;


import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional 
public class AcademyCategoryService {
    private final AcademyCategoryRepository academyCategoryRepository;
    private final AcademyCategoryMapper academyCategoryMapper;
    private final AcademyService academyService;
    private final CategoryService categoryService;
    private final SeasonService seasonService;

    private final AcademyMapper academyMapper;
    private final CategoryMapper categoryMapper;
    private final SeasonMapper seasonMapper;

    public AcademyCategoryResponse createAcademyCategory(AcademyCategoryCreateRequest request) {

        var season = seasonMapper.toEntity(seasonService.getSeasonById(request.seasonId()));
        var academy = academyMapper.toEntity(academyService.getAcademyById(request.academyId()));
        var category = categoryMapper.toEntity(categoryService.getCategoryById(request.categoryId()));

        return academyCategoryMapper
                .toResponse(academyCategoryRepository
                        .save(academyCategoryMapper.toEntity(request, academy, season, category)));
    }

    @Transactional(readOnly = true)
    public AcademyCategoryResponse getAcademyCategoryById(UUID academyCategoryId) {
        var academyCategory = academyCategoryRepository.findById(academyCategoryId).orElseThrow(
                () -> new NotFoundException("AcademyCategory with id " + academyCategoryId + " not found"));

        return academyCategoryMapper.toResponse(academyCategory);
    }

}
