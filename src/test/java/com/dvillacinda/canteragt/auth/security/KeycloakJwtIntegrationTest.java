package com.dvillacinda.canteragt.auth.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;

import tools.jackson.databind.ObjectMapper;

class KeycloakJwtIntegrationTest {
    @Test
    void keycloakIssuedAccessTokenPassesIssuerAndSignatureValidation() throws Exception {
        String issuer = env("KEYCLOAK_ISSUER_URL");
        String clientId = env("KEYCLOAK_ADMIN_CLIENT_ID");
        String clientSecret = env("KEYCLOAK_ADMIN_CLIENT_SECRET");
        assumeTrue(issuer != null && clientId != null && clientSecret != null,
                "Keycloak integration environment is not configured");

        String form = "grant_type=client_credentials&client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret);
        HttpRequest request = HttpRequest.newBuilder(URI.create(issuer + "/protocol/openid-connect/token"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), "Keycloak client credentials token request failed");

        String token = new ObjectMapper().readTree(response.body()).path("access_token").asString(null);
        assertNotNull(token, "Keycloak token response did not contain access_token");

        JwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuer);
        var jwt = decoder.decode(token);
        assertEquals(issuer.replaceAll("/+$", ""), jwt.getIssuer().toString().replaceAll("/+$", ""));
        assertNotNull(new KeycloakJwtAuthenticationConverter(clientId).convert(jwt));
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : value;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
