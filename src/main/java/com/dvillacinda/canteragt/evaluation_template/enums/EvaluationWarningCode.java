package com.dvillacinda.canteragt.evaluation_template.enums;

/** Non-blocking warnings returned when an evaluation is recorded in an event (T4.4-D3, ADR-0004 DP-2.3). */
public enum EvaluationWarningCode {
    /** The player's last non-voided evaluation with the same template is younger than frequency_min_days. */
    FREQUENCY_TOO_SOON,
    /** That same evaluation is still valid (valid_until is today or later). */
    PREVIOUS_STILL_VALID,
    /** Fewer players PRESENT or LATE in the session than the template's group_size_min. */
    GROUP_TOO_SMALL
}
