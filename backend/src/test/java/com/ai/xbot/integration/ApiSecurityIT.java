package com.ai.xbot.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ai.xbot.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
class ApiSecurityIT extends AbstractPostgresIT {

    @Autowired MockMvc mvc;
    @Autowired AuditLogRepository auditLogRepository;

    private MockHttpSession login() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", "it-admin").param("password", "it-admin-password-123"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void apiRequiresLogin() throws Exception {
        mvc.perform(get("/api/dashboard/overview")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/settings/schedule")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", "it-admin").param("password", "wrong-password"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithoutCsrfIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .param("username", "it-admin").param("password", "it-admin-password-123"))
                .andExpect(status().isForbidden());
    }

    @Test
    void bootstrappedAdminCanLogInAndReadDashboard() throws Exception {
        MockHttpSession session = login();
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("it-admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
        mvc.perform(get("/api/dashboard/overview").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byStatus.PUBLISHED").exists());
    }

    @Test
    void systemStatusNeverLeaksSecrets() throws Exception {
        MockHttpSession session = login();
        String body = mvc.perform(get("/api/system/status").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishingEnabled").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContainIgnoringCase("secret").doesNotContainIgnoringCase("apiKey");
    }

    @Test
    void settingsUpdateNeedsCsrfAndIsAudited() throws Exception {
        MockHttpSession session = login();
        String json = """
                {"timezone":"Asia/Kolkata","morning":"08:15","afternoon":"13:00","evening":"19:00"}""";

        mvc.perform(put("/api/settings/schedule").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isForbidden());

        long auditsBefore = auditLogRepository.count();
        mvc.perform(put("/api/settings/schedule").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.morning").value("08:15"));
        assertThat(auditLogRepository.count()).isGreaterThan(auditsBefore);

        // restore default
        mvc.perform(put("/api/settings/schedule").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.replace("08:15", "08:00"))).andExpect(status().isOk());
    }

    @Test
    void invalidScheduleReturnsFieldErrors() throws Exception {
        MockHttpSession session = login();
        mvc.perform(put("/api/settings/schedule").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timezone\":\"Asia/Kolkata\",\"morning\":\"25:00\",\"afternoon\":\"13:00\",\"evening\":\"19:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("morning"));
    }

    @Test
    void languageMixMustTotal100() throws Exception {
        MockHttpSession session = login();
        mvc.perform(put("/api/settings/content").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabledLanguages\":[\"ta\",\"en\"],\"languageMix\":{\"ta\":70,\"en\":20},"
                                + "\"variationsPerTopic\":2,\"duplicateLookbackDays\":30}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("100")));
    }
}
