package com.dvillacinda.canteragt.academy.mapper;

import org.springframework.stereotype.Component;

import com.dvillacinda.canteragt.academy.dto.AcademyCreateRequest;
import com.dvillacinda.canteragt.academy.dto.AcademyResponse;
import com.dvillacinda.canteragt.academy.entity.AcademyEntity;


@Component 
public class AcademyMapper {
    public AcademyResponse toResponse(AcademyEntity academyEntity) {
        return new AcademyResponse(
            academyEntity.getAcademyId(),
            academyEntity.getName(),
            academyEntity.getStatus(),
            academyEntity.getCreatedAt(),
            academyEntity.getUpdatedAt()
        );
    }


    public AcademyEntity toEntity(AcademyCreateRequest academyResponse) {
        return AcademyEntity.builder()
            .name(academyResponse.name())
            .status(academyResponse.status())
            .build();
    }

    public AcademyEntity toEntity(AcademyResponse academyResponse) {
        return AcademyEntity.builder()
            .academyId(academyResponse.academyId())
            .name(academyResponse.name())
            .status(academyResponse.status())
            .build();
    }

}
