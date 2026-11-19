package com.dvillacinda.canteragt.training_session.service;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import com.dvillacinda.canteragt.shared.exception.ConflictException;
import com.dvillacinda.canteragt.training_session.entity.TrainingSessionEntity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;

/**
 * Serializes changes to a session's children (roster, attendance, staff) with its lifecycle.
 *
 * <p>{@link #acquire} reloads the session under {@code SELECT … FOR UPDATE} so status checks see the latest
 * committed state, then bumps its {@code @Version}. Concurrent child mutations therefore wait for each other
 * instead of failing, and a concurrent optimistic transition (e.g. {@code complete}) conflicts with 409 instead of
 * interleaving. Must be called inside the caller's transaction, after authorization and before any rule check.
 */
@Component
@RequiredArgsConstructor
public class TrainingSessionLock {
    static final String CONCURRENT_MODIFICATION = "Training session was modified concurrently; reload and retry";

    private final EntityManager entityManager;

    public TrainingSessionEntity acquire(TrainingSessionEntity session) {
        try {
            entityManager.refresh(session, LockModeType.PESSIMISTIC_WRITE);
            entityManager.lock(session, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        } catch (OptimisticLockingFailureException | OptimisticLockException ex) {
            throw new ConflictException(CONCURRENT_MODIFICATION);
        }
        return session;
    }

    /** Flushes pending child changes, translating a version conflict into 409. */
    public void flush() {
        try {
            entityManager.flush();
        } catch (OptimisticLockingFailureException | OptimisticLockException ex) {
            throw new ConflictException(CONCURRENT_MODIFICATION);
        }
    }
}
