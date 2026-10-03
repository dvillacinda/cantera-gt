package com.dvillacinda.canteragt.category.dto;

import com.dvillacinda.canteragt.category.enums.CategoryCode;


public record CategoryUpdateRequest(
    String name,
    Integer minAge,
    Integer maxAge,
    CategoryCode categoryCode
) {
    
}
