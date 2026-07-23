package com.hansungteam.ersync.global;

import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ApiFoundationIntegrationTest.FoundationTestController.class)
@ExtendWith(OutputCaptureExtension.class)
class ApiFoundationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointIsPublicAndHasTraceId() throws Exception {
        mockMvc.perform(get("/api/system/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"));
    }

    @Test
    void actuatorLivenessAndReadinessArePublic() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void protectedEndpointReturnsStandardUnauthorizedResponse(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/api/private"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.code").value("AUTH_001"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        assertThat(output)
                .contains("event=AUTH_ERROR")
                .contains("code=AUTH_001")
                .contains("status=401")
                .contains("method=GET");
    }

    @Test
    @WithMockUser(roles = "PARAMEDIC")
    void methodRoleFailureReturnsStandardForbiddenResponse() throws Exception {
        mockMvc.perform(get("/test/foundation/admin"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_003"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @WithMockUser
    void customExceptionReturnsRegisteredErrorCode(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/test/foundation/custom-error"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HOSPITAL_001"))
                .andExpect(jsonPath("$.message").value("병원을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        assertThat(output)
                .contains("event=BUSINESS_ERROR")
                .contains("code=HOSPITAL_001")
                .contains("path=/test/foundation/custom-error");
    }

    @Test
    @WithMockUser
    void validationFailureReturnsFieldErrors() throws Exception {
        mockMvc.perform(post("/test/foundation/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("NotBlank"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @WithMockUser
    void unexpectedExceptionReturnsSafeResponseAndSystemLog(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/test/foundation/system-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COMMON_003"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류입니다."))
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        assertThat(output)
                .contains("event=SYSTEM_ERROR")
                .contains("code=COMMON_003")
                .contains("exception=IllegalStateException");
    }

    @RestController
    @RequestMapping("/test/foundation")
    static class FoundationTestController {

        @GetMapping("/admin")
        @PreAuthorize("hasRole('SUPER_ADMIN')")
        String adminOnly() {
            return "ok";
        }

        @GetMapping("/custom-error")
        String customError() {
            throw new CustomException(ErrorCode.HOSPITAL_NOT_FOUND);
        }

        @PostMapping("/validation")
        String validate(@Valid @RequestBody FoundationRequest request) {
            return request.name();
        }

        @GetMapping("/system-error")
        String systemError() {
            throw new IllegalStateException("internal detail must not be returned");
        }
    }

    record FoundationRequest(@NotBlank String name) {
    }
}
