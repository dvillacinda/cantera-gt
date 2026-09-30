package com.dvillacinda.canteragt.auth.service;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.dvillacinda.canteragt.user.dto.UserCreateRequest;

@Service
public class KeycloakUserService {
    private final RestClient restClient;
    private final String serverUrl;
    private final String realm;
    private final String clientId;
    private final String clientSecret;

    public KeycloakUserService(
            RestClient.Builder restClientBuilder,
            @Value("${app.keycloak.server-url}") String serverUrl,
            @Value("${app.keycloak.realm}") String realm,
            @Value("${app.keycloak.admin-client-id}") String clientId,
            @Value("${app.keycloak.admin-client-secret}") String clientSecret) {
        this.restClient = restClientBuilder.build();
        this.serverUrl = serverUrl.replaceAll("/$", "");
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public String createUser(UserCreateRequest request) {
        String token = accessToken();
        Map<String, Object> body = Map.of(
                "username", request.username(),
                "email", request.email(),
                "enabled", true,
                "emailVerified", false,
                "requiredActions", List.of("UPDATE_PASSWORD"));

        URI location = restClient.post()
                .uri(adminRealmUrl() + "/users")
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((requestEntity, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new IllegalStateException("Keycloak could not create the user: " + response.getStatusCode());
                    }
                    return response.getHeaders().getLocation();
                });
        if (location == null || location.getPath() == null) {
            throw new IllegalStateException("Keycloak created the user without returning its identifier");
        }
        String keycloakId = location.getPath().substring(location.getPath().lastIndexOf('/') + 1);
        if (keycloakId.isBlank()) {
            throw new IllegalStateException("Keycloak returned an empty user identifier");
        }
        return keycloakId;
    }

    public void deleteUser(String keycloakId) {
        String token = accessToken();
        restClient.delete()
                .uri(adminRealmUrl() + "/users/{id}", keycloakId)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .toBodilessEntity();
    }

    public void assignRealmRole(String keycloakId, String roleName) {
        String token = accessToken();
        Map<?, ?> role = restClient.get()
                .uri(adminRealmUrl() + "/roles/{roleName}", roleName)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .body(Map.class);
        if (role == null || role.get("id") == null || role.get("name") == null) {
            throw new IllegalStateException("Keycloak realm role does not exist: " + roleName);
        }
        restClient.post()
                .uri(adminRealmUrl() + "/users/{id}/role-mappings/realm", keycloakId)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(role))
                .retrieve()
                .toBodilessEntity();
    }

    private String accessToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        Map<?, ?> response = restClient.post()
                .uri(serverUrl + "/realms/" + realm + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);
        if (response == null || response.get("access_token") == null) {
            throw new IllegalStateException("Keycloak did not return an administrative access token");
        }
        return response.get("access_token").toString();
    }

    private String adminRealmUrl() {
        return serverUrl + "/admin/realms/" + realm;
    }
}
