package com.dvillacinda.canteragt.academy.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.dvillacinda.canteragt.academy.enums.Status;


public record AcademyResponse(
    UUID academyId,
    String name,
    Status status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {

}
