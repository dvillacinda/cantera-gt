package com.dvillacinda.canteragt.evaluation_template.controller;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.dvillacinda.canteragt.evaluation_template.dto.EvaluationTemplateResponse;
import com.dvillacinda.canteragt.evaluation_template.service.EvaluationTemplateService;
import com.dvillacinda.canteragt.auth.security.RestAuthenticationEntryPoint;
import com.dvillacinda.canteragt.auth.security.KeycloakJwtAuthenticationConverter;
import com.dvillacinda.canteragt.shared.config.SecurityConfig;

@WebMvcTest(value = EvaluationTemplateController.class, properties = {
        "app.security.cors.allowed-origins=http://localhost",
        "app.security.oauth2.client-id=test",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.test"
})
@Import({ EvaluationTemplateController.class,
        SecurityConfig.class,
        EvaluationTemplateSecurityHttpTest.WebSecurityEnabler.class,
        RestAuthenticationEntryPoint.class })
class EvaluationTemplateSecurityHttpTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EvaluationTemplateService evaluationTemplateService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private KeycloakJwtAuthenticationConverter jwtAuthenticationConverter;

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class WebSecurityEnabler {}

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/evaluation-templates/get-all-templates"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidBearerTokenIsUnauthorized() throws Exception {
        when(jwtDecoder.decode("broken")).thenThrow(new BadJwtException("invalid token"));

        mockMvc.perform(get("/api/v1/evaluation-templates/get-all-templates")
                        .header("Authorization", "Bearer broken"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(evaluationTemplateService);
    }

    @Test
    void academyAdminAndCoachCanReadTemplatesButPlayerCannot() throws Exception {
        when(evaluationTemplateService.getAllEvaluationTemplates()).thenReturn(List.<EvaluationTemplateResponse>of());

        mockMvc.perform(get("/api/v1/evaluation-templates/get-all-templates")
                        .with(user("admin").roles("ACADEMY_ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/evaluation-templates/get-all-templates")
                        .with(user("coach").roles("COACH")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/evaluation-templates/get-all-templates")
                        .with(user("player").roles("PLAYER")))
                .andExpect(status().isForbidden());
    }
}
