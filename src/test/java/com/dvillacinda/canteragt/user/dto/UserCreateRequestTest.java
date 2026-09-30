package com.dvillacinda.canteragt.user.dto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class UserCreateRequestTest {
    @Test
    void requestDoesNotExposeKeycloakId() {
        String[] componentNames = Arrays.stream(UserCreateRequest.class.getRecordComponents())
                .map(component -> component.getName())
                .toArray(String[]::new);

        assertArrayEquals(new String[] { "email", "username", "status" }, componentNames);
        assertEquals(3, UserCreateRequest.class.getRecordComponents().length);
    }
}
