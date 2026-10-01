package com.dvillacinda.canteragt.shared.enums;

public enum Status {
    ACTIVE,
    INACTIVE,
    LOCKED,
    PENDING;

    /** Whether the Keycloak account must stay enabled. PENDING can log in to finish its onboarding. */
    public boolean canLogIn() {
        return this == ACTIVE || this == PENDING;
    }
}
