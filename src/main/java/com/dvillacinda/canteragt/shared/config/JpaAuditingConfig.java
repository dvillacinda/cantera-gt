package com.dvillacinda.canteragt.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables automatic JPA auditing.
 *
 * Thanks to this annotation, fields annotated with:
 * - @CreatedDate
 * - @LastModifiedDate
 * - @CreatedBy
 * - @LastModifiedBy
 *
 * are automatically populated when persisting or updating an entity
 * that extends BaseEntity (or that
 * uses @EntityListeners(AuditingEntityListener.class)).
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
