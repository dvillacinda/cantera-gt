package com.dvillacinda.canteragt.position.mapper;

import org.springframework.stereotype.Component;

import com.dvillacinda.canteragt.position.dto.PositionResponse;
import com.dvillacinda.canteragt.position.entity.PositionEntity;

@Component
public class PositionMapper {
    public PositionResponse toResponse(PositionEntity position) {
        return new PositionResponse(
                position.getPositionId(),
                position.getPositionCode(),
                position.getPositionLine(),
                position.getName());
    }
}
