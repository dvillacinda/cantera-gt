package com.dvillacinda.canteragt.academy.dto;

import com.dvillacinda.canteragt.academy.enums.Status;

public record AcademyUpdateRequest(
    String name,
    Status status
) {
    
}
