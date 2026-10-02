package com.dvillacinda.canteragt.academy.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dvillacinda.canteragt.academy.entity.AcademyEntity;

public interface AcademyRepository extends JpaRepository<AcademyEntity, UUID> {

}
