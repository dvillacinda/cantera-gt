package com.dvillacinda.canteragt.evaluation_template.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dvillacinda.canteragt.evaluation_template.entity.EvaluationTemplateEntity;

public interface EvaluationTemplateRepository extends JpaRepository <EvaluationTemplateEntity, UUID>{
    
}
