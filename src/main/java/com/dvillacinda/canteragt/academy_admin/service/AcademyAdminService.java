package com.dvillacinda.canteragt.academy_admin.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dvillacinda.canteragt.academy.mapper.AcademyMapper;
import com.dvillacinda.canteragt.academy.service.AcademyService;
import com.dvillacinda.canteragt.academy_admin.dto.AcademyAdminCreateRequest;
import com.dvillacinda.canteragt.academy_admin.dto.AcademyAdminResponse;
import com.dvillacinda.canteragt.academy_admin.dto.AcademyAdminUpdateRequest;
import com.dvillacinda.canteragt.academy_admin.entity.AcademyAdminEntity;
import com.dvillacinda.canteragt.academy_admin.mapper.AcademyAdminMapper;
import com.dvillacinda.canteragt.academy_admin.repository.AcademyAdminRepository;
import com.dvillacinda.canteragt.shared.exception.ConflictException;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;
import com.dvillacinda.canteragt.user.mapper.UserMapper;
import com.dvillacinda.canteragt.user.service.UserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class AcademyAdminService {
    private final AcademyAdminRepository academyAdminRepository;
    private final AcademyAdminMapper academyAdminMapper;
    private final UserService userService;
    private final AcademyService academyService;
    private final AcademyMapper academyMapper;
    private final UserMapper userMapper;

    public AcademyAdminResponse createAcademyAdmin(AcademyAdminCreateRequest request) {

        var userExist = userMapper.toEntity(userService.getUserById(request.userId()));
        if (!userService.hasKeycloakRealmRole(userExist.getKeycloakId(), "ACADEMY_ADMIN")) {
            throw new ConflictException("User must have the ACADEMY_ADMIN role to be assigned to an academy");
        }
        var academyExist = academyMapper.toEntity(academyService.getAcademyById(request.academyId()));

        return academyAdminMapper
                .toResponse(academyAdminRepository.save(academyAdminMapper.toEntity(request, userExist, academyExist)));
    }

    public AcademyAdminResponse updateAcademyAdmin(UUID academyAdminId, AcademyAdminUpdateRequest request) {
        AcademyAdminEntity academyAdminExist = academyAdminRepository.findById(academyAdminId)
                .orElseThrow(() -> new NotFoundException("Academy Admin not found"));

        if (request.status() != null)
            academyAdminExist.setStatus(request.status());

        if (request.academyId() != null) {
            var academyExist = academyMapper.toEntity(academyService.getAcademyById(request.academyId()));
            academyAdminExist.setAcademy(academyExist);
        }

        if (request.userId() != null) {
            var userExist = userMapper.toEntity(userService.getUserById(request.userId()));
            academyAdminExist.setUser(userExist);
        }

        return academyAdminMapper.toResponse(academyAdminRepository.save(academyAdminExist));
    }

}
