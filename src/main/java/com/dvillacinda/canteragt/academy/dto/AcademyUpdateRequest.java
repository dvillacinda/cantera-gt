package com.dvillacinda.canteragt.academy.dto;

import com.dvillacinda.canteragt.shared.enums.Status;

public record AcademyUpdateRequest(
    String name,
    Status status
) {
    
}
