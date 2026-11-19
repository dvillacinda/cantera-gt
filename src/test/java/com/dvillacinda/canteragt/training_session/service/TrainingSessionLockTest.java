package com.dvillacinda.canteragt.training_session.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.dvillacinda.canteragt.shared.exception.ConflictException;
import com.dvillacinda.canteragt.training_session.entity.TrainingSessionEntity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;

@ExtendWith(MockitoExtension.class)
class TrainingSessionLockTest {
    @Mock private EntityManager entityManager;
    @InjectMocks private TrainingSessionLock lock;

    private final TrainingSessionEntity session = new TrainingSessionEntity();

    @Test
    void acquireReloadsUnderRowLockThenBumpsVersion() {
        assertSame(session, lock.acquire(session));

        InOrder order = inOrder(entityManager);
        order.verify(entityManager).refresh(session, LockModeType.PESSIMISTIC_WRITE);
        order.verify(entityManager).lock(session, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
    }

    @Test
    void versionConflictWhileLockingBecomes409() {
        doThrow(new OptimisticLockException("stale"))
                .when(entityManager).lock(session, LockModeType.PESSIMISTIC_FORCE_INCREMENT);

        ConflictException ex = assertThrows(ConflictException.class, () -> lock.acquire(session));
        assertEquals(TrainingSessionLock.CONCURRENT_MODIFICATION, ex.getMessage());
    }

    @Test
    void versionConflictWhileFlushingBecomes409() {
        doThrow(new ObjectOptimisticLockingFailureException(TrainingSessionEntity.class, "id"))
                .when(entityManager).flush();

        assertThrows(ConflictException.class, () -> lock.flush());
    }
}
