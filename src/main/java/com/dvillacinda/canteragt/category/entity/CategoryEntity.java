package com.dvillacinda.canteragt.category.entity;

import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import com.dvillacinda.canteragt.category.enums.CategoryCode;
import com.dvillacinda.canteragt.shared.entity.BaseEntity;
import com.dvillacinda.canteragt.shared.enums.Status;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
@Entity 
@Table(name = "categories")
@AllArgsConstructor 
@NoArgsConstructor 
@Getter 
@Setter 
@Builder 
public class CategoryEntity extends BaseEntity{
    @Id 
    @GeneratedValue 
    @UuidGenerator (style = UuidGenerator.Style.VERSION_7)
    @Column (name = "category_id")
    private UUID categoryId;

    @Column (name = "name", nullable = false, length = 100)
    private String name;

    @Column (name = "max_age", nullable = false)
    private Integer maxAge;

    @Column (name = "min_age", nullable = false)
    private Integer minAge;

    @Column (name = "status", nullable = false)
    @Enumerated (value = EnumType.STRING)
    private Status status;

    @Column (name = "code", nullable = false)
    @Enumerated (value = EnumType.STRING)
    private CategoryCode categoryCode;
}
