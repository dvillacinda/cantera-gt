package com.dvillacinda.canteragt.training_session.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dvillacinda.canteragt.training_session.entity.SessionEventMetricEntity;
import com.dvillacinda.canteragt.training_session.entity.SessionEventMetricId;

public interface SessionEventMetricRepository extends JpaRepository<SessionEventMetricEntity, SessionEventMetricId> {

    /** Metrics of several events in one query, with the criterion joined (no N+1 when listing a session). */
    @Query("""
            select m from SessionEventMetricEntity m
              join fetch m.criterion c
            where m.id.sessionEventId in :eventIds
            order by c.displayOrder, c.code
            """)
    List<SessionEventMetricEntity> findForEvents(@Param("eventIds") Collection<UUID> eventIds);
}
