package com.dvillacinda.canteragt.template_dimension;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "dimensionCode")
public class TemplateDimension {
    @Column(name = "dimension_code", nullable = false)
    private String dimensionCode;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

}
