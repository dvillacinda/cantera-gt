package com.dvillacinda.canteragt.training_session.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum AttendanceStatus {
    PENDING,
    PRESENT,
    ABSENT,
    JUSTIFIED,
    LATE;

    /** Attendance that allows a player to be evaluated in an event (ADR-0004, DP-2.3). */
    public static final Set<AttendanceStatus> EVALUABLE = Collections.unmodifiableSet(EnumSet.of(PRESENT, LATE));
}
