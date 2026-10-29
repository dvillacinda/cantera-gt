package com.dvillacinda.canteragt.academy_location.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.dvillacinda.canteragt.academy_location.entity.AcademyLocationEntity;
import com.dvillacinda.canteragt.shared.enums.Status;

public interface AcademyLocationRepository extends JpaRepository<AcademyLocationEntity, UUID> {
    @EntityGraph(attributePaths = "academy")
    List<AcademyLocationEntity> findAllByAcademy_AcademyIdAndStatus(UUID academyId, Status status);
}
