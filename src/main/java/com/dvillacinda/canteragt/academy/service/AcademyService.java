package com.dvillacinda.canteragt.academy.service;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dvillacinda.canteragt.academy.dto.AcademyCreateRequest;
import com.dvillacinda.canteragt.academy.dto.AcademyResponse;
import com.dvillacinda.canteragt.academy.dto.AcademyUpdateRequest;
import com.dvillacinda.canteragt.academy.entity.AcademyEntity;
import com.dvillacinda.canteragt.academy.mapper.AcademyMapper;
import com.dvillacinda.canteragt.academy.repository.AcademyRepository;
import com.dvillacinda.canteragt.shared.enums.Status;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class AcademyService {
    private final AcademyRepository academyRepository;
    private final AcademyMapper academyMapper;

    public AcademyResponse createAcademy(AcademyCreateRequest academyRequest) {
        AcademyEntity academyEntity = academyRepository.save(academyMapper.toEntity(academyRequest));
        return academyMapper.toResponse(academyEntity);

    }

    @Transactional(readOnly = true)
    public AcademyResponse getAcademyById(UUID academyId) {
        AcademyEntity academyEntity = academyRepository.findById(academyId)
                .orElseThrow(() -> new NotFoundException("Academy not found with id " + academyId));
        return academyMapper.toResponse(academyEntity);
    }

    @Transactional(readOnly = true)
    public Set<AcademyResponse> getAllAcademies() {
        return academyRepository
                .findAll()
                .stream()
                .map(academyMapper::toResponse).collect(Collectors.toSet());
    }

    public void deleteAcademyById(UUID academyId) {
        AcademyEntity academy = academyRepository.findById(academyId)
                .orElseThrow(() -> new NotFoundException("Academy not found with id " + academyId));

        academyRepository.delete(academy);
    }

    public AcademyResponse updateAcademy(UUID academyId, AcademyUpdateRequest academyRequest) {
        AcademyEntity academy = academyRepository.findById(academyId)
                .orElseThrow(() -> new NotFoundException("Academy not found with id " + academyId));

        if (academyRequest.name() != null)
            academy.setName(academyRequest.name());
        academyRepository.save(academy);
        return academyMapper.toResponse(academy);
    }

    public AcademyResponse updateAcademyStatusById(UUID academyId, Status status) {
        AcademyEntity academy = academyRepository.findById(academyId)
                .orElseThrow(() -> new NotFoundException("Academy not found with id " + academyId));
        academy.setStatus(status);
        return academyMapper.toResponse(academyRepository.save(academy));
    }

}
