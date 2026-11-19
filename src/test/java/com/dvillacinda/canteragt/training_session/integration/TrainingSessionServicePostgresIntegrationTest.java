package com.dvillacinda.canteragt.training_session.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.dvillacinda.canteragt.academy.entity.AcademyEntity;
import com.dvillacinda.canteragt.support.TestDataFactory;
import com.dvillacinda.canteragt.academy_category.entity.AcademyCategoryEntity;
import com.dvillacinda.canteragt.academy_location.entity.AcademyLocationEntity;
import com.dvillacinda.canteragt.coach.entity.CoachEntity;
import com.dvillacinda.canteragt.shared.exception.ConflictException;
import com.dvillacinda.canteragt.training_session.dto.TrainingSessionFilter;
import com.dvillacinda.canteragt.training_session.dto.TrainingSessionResponse;
import com.dvillacinda.canteragt.training_session.entity.TrainingSessionEntity;
import com.dvillacinda.canteragt.training_session.enums.TrainingSessionStatus;
import com.dvillacinda.canteragt.training_session.repository.TrainingSessionRepository;
import com.dvillacinda.canteragt.training_session.service.TrainingSessionAccessPolicy;
import com.dvillacinda.canteragt.training_session.service.TrainingSessionService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

/**
 * Service-level behaviour of training sessions that only a real PostgreSQL can prove: optimistic locking under
 * concurrent lifecycle transitions (rule 8) and the absence of N+1 queries in the paginated listing.
 * The access policy is mocked because authorization is covered by its own unit and HTTP tests.
 * Requires the isolated {@code postgres-test} database (see docs/testing.md).
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class TrainingSessionServicePostgresIntegrationTest {
    private static final LocalDateTime SESSION_DATE = LocalDateTime.of(2026, 10, 1, 10, 0);

    @Autowired private TrainingSessionService trainingSessionService;
    @Autowired private TrainingSessionRepository trainingSessionRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private TransactionTemplate transactionTemplate;

    @MockitoBean private TrainingSessionAccessPolicy accessPolicy;

    private TestDataFactory factory;

    @Test
    void concurrentStartLetsExactlyOneWinAndTheOtherGetsConflict() throws Exception {
        Fixture fixture = createFixture();
        UUID sessionId = inTransaction(() -> saveSession(fixture, "Concurrent start").getSessionId());

        // Both threads load the session (version 0) before either one writes: the barrier sits in the access
        // check, which the service runs after reading the session and before changing its status.
        CyclicBarrier bothLoaded = new CyclicBarrier(2);
        doAnswer(invocation -> {
            bothLoaded.await(10, TimeUnit.SECONDS);
            return null;
        }).when(accessPolicy).requireManageAccess(any(), any(), any());

        Callable<TrainingSessionResponse> start =
                () -> trainingSessionService.start(sessionId, fixture.academyId(), null);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Object> outcomes = new ArrayList<>();
        try {
            List<Future<TrainingSessionResponse>> futures = executor.invokeAll(List.of(start, start), 30,
                    TimeUnit.SECONDS);
            for (Future<TrainingSessionResponse> future : futures) {
                try {
                    outcomes.add(future.get());
                } catch (java.util.concurrent.ExecutionException ex) {
                    outcomes.add(ex.getCause());
                }
            }
        } finally {
            executor.shutdownNow();
        }

        List<TrainingSessionResponse> winners = outcomes.stream()
                .filter(TrainingSessionResponse.class::isInstance).map(TrainingSessionResponse.class::cast).toList();
        List<Object> losers = outcomes.stream().filter(o -> !(o instanceof TrainingSessionResponse)).toList();
        assertEquals(1, winners.size(), "exactly one start must succeed: " + outcomes);
        assertEquals(1, losers.size(), "exactly one start must fail: " + outcomes);
        assertInstanceOf(ConflictException.class, losers.getFirst());
        assertEquals(TrainingSessionStatus.IN_PROGRESS, winners.getFirst().status());

        TrainingSessionEntity persisted = inTransaction(() ->
                trainingSessionRepository.findById(sessionId).orElseThrow());
        assertEquals(TrainingSessionStatus.IN_PROGRESS, persisted.getStatus());
        assertEquals(1L, persisted.getVersion());
    }

    @Test
    void listingIssuesConstantQueriesRegardlessOfPageContent() {
        Fixture fixture = createFixture();
        inTransaction(() -> {
            for (int i = 0; i < 6; i++) {
                saveSession(fixture, "Listing session " + i);
            }
            return null;
        });

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        var page = trainingSessionService.list(
                fixture.academyCategoryId(),
                fixture.academyId(),
                new TrainingSessionFilter(null, null, null),
                PageRequest.of(0, 4),
                null);

        assertEquals(6, page.getTotalElements());
        assertEquals(4, page.getContent().size());
        page.getContent().forEach(session -> {
            assertEquals(fixture.locationId(), session.locationId());
            assertEquals(fixture.coachId(), session.createdByCoachId());
        });
        // one select for the page (with its to-one associations joined) + one count query; no per-row loads
        assertTrue(statistics.getPrepareStatementCount() <= 2,
                "expected at most 2 statements but got " + statistics.getPrepareStatementCount());
        assertEquals(0, statistics.getEntityFetchCount(), "lazy associations were fetched one by one (N+1)");
    }

    private TrainingSessionEntity saveSession(Fixture fixture, String name) {
        return factory.session(fixture.academyCategoryId(), fixture.coachId(), fixture.locationId(), name,
                SESSION_DATE, TrainingSessionStatus.PLANNED, null, null);
    }

    private Fixture createFixture() {
        factory = new TestDataFactory(entityManager);
        return inTransaction(() -> {
            AcademyEntity academy = factory.academy("Session service");
            AcademyCategoryEntity academyCategory =
                    factory.academyCategory(academy, "SESSION_SVC_IT", LocalDate.of(2026, 6, 1));
            AcademyLocationEntity location = factory.location(academy, "Service");
            CoachEntity coach = factory.coach("session-svc-coach", "Session");
            entityManager.flush();
            return new Fixture(academy.getAcademyId(), academyCategory.getAcademyCategoryId(),
                    location.getLocationId(), coach.getCoachId());
        });
    }

    private <T> T inTransaction(Supplier<T> supplier) {
        return transactionTemplate.execute(ignored -> supplier.get());
    }

    private record Fixture(UUID academyId, UUID academyCategoryId, UUID locationId, UUID coachId) { }
}
