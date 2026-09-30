package com.dvillacinda.canteragt.user.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dvillacinda.canteragt.user.entity.UserEntity;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    
}
