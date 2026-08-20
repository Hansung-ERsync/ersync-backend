package com.hansungteam.ersync.organization;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminOrganizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void superAdminCreatesAndListsOrganizations() throws Exception {
        mockMvc.perform(post("/api/v1/admin/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "한성대학교병원",
                                  "type": "HOSPITAL"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizationId").isNotEmpty())
                .andExpect(jsonPath("$.name").value("한성대학교병원"))
                .andExpect(jsonPath("$.type").value("HOSPITAL"))
                .andExpect(jsonPath("$.status").doesNotExist());

        mockMvc.perform(get("/api/v1/admin/organizations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("한성대학교병원"))
                .andExpect(jsonPath("$.items[0].status").doesNotExist())
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.page").doesNotExist())
                .andExpect(jsonPath("$.size").doesNotExist())
                .andExpect(jsonPath("$.totalElements").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "PARAMEDIC")
    void paramedicCannotManageOrganizations() throws Exception {
        mockMvc.perform(get("/api/v1/admin/organizations"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void invalidOrganizationRequestUsesCommonValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/admin/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "type": "HOSPITAL"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }
}
