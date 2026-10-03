package com.dvillacinda.canteragt.academy_admin.mapper;

import org.springframework.stereotype.Component;

import com.dvillacinda.canteragt.academy.entity.AcademyEntity;
import com.dvillacinda.canteragt.academy_admin.dto.AcademyAdminCreateRequest;
import com.dvillacinda.canteragt.academy_admin.dto.AcademyAdminResponse;
import com.dvillacinda.canteragt.academy_admin.entity.AcademyAdminEntity;
import com.dvillacinda.canteragt.user.entity.UserEntity;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor 
public class AcademyAdminMapper {
    
    public AcademyAdminResponse toResponse(AcademyAdminEntity academyAdminEntity) {
        return new AcademyAdminResponse(academyAdminEntity.getAcademyAdminId(),
                academyAdminEntity.getAcademy().getAcademyId(),
                academyAdminEntity.getUser().getUserId(),
                academyAdminEntity.getStatus(),
                academyAdminEntity.getCreatedAt(),
                academyAdminEntity.getUpdatedAt());
    }

    public AcademyAdminEntity toEntity(AcademyAdminCreateRequest request, UserEntity user, AcademyEntity academy) {
        return AcademyAdminEntity.builder()
                .academy(academy)
                .user(user)
                .status(request.status())
                .build();
    }

}
