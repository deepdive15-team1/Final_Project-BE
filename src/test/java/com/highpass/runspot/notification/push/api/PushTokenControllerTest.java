package com.highpass.runspot.notification.push.api;

import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.highpass.runspot.common.config.SecurityConfig;
import com.highpass.runspot.common.exception.handler.ApiExceptionHandler;
import com.highpass.runspot.common.jwt.JwtAuthenticationFilter;
import com.highpass.runspot.common.security.UserPrincipal;
import com.highpass.runspot.notification.push.domain.PushPlatform;
import com.highpass.runspot.notification.push.exception.PushDeviceTokenErrorCode;
import com.highpass.runspot.notification.push.exception.PushDeviceTokenException;
import com.highpass.runspot.notification.push.service.PushDeviceTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PushTokenController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ApiExceptionHandler.class, SecurityConfig.class})
class PushTokenControllerTest {

    private static final long USER_ID = 10L;
    private static final String BASE_URL = "/users/me/push-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PushDeviceTokenService pushDeviceTokenService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void authenticate() {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(new UserPrincipal(USER_ID), null));
        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 인증된_사용자는_한_글자_안드로이드_토큰을_등록하고_204를_받는다() throws Exception {
        mockMvc.perform(put(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"a\",\"platform\":\"ANDROID\"}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        then(pushDeviceTokenService).should()
                .upsert(USER_ID, "a", PushPlatform.ANDROID);
    }

    @Test
    void 인증된_사용자는_512자_안드로이드_토큰을_등록하고_204를_받는다() throws Exception {
        String maximumLengthToken = "a".repeat(512);

        mockMvc.perform(put(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + maximumLengthToken + "\",\"platform\":\"ANDROID\"}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        then(pushDeviceTokenService).should()
                .upsert(USER_ID, maximumLengthToken, PushPlatform.ANDROID);
    }

    @Test
    void 인증된_사용자는_토큰을_삭제하고_204를_받는다() throws Exception {
        mockMvc.perform(delete(BASE_URL))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        then(pushDeviceTokenService).should().delete(USER_ID);
    }

    @Test
    void 빈_토큰은_표준_검증_오류로_거부되고_서비스를_호출하지_않는다() throws Exception {
        mockMvc.perform(put(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\" \",\"platform\":\"ANDROID\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400));

        then(pushDeviceTokenService).shouldHaveNoInteractions();
    }

    @Test
    void 토큰이_513자이면_표준_검증_오류로_거부되고_서비스를_호출하지_않는다() throws Exception {
        String oversizedToken = "a".repeat(513);

        mockMvc.perform(put(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + oversizedToken + "\",\"platform\":\"ANDROID\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400));

        then(pushDeviceTokenService).shouldHaveNoInteractions();
    }

    @Test
    void 지원하지_않는_플랫폼은_표준_검증_오류로_거부되고_서비스를_호출하지_않는다() throws Exception {
        mockMvc.perform(put(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"android-token\",\"platform\":\"IOS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400));

        then(pushDeviceTokenService).shouldHaveNoInteractions();
    }

    @Test
    void 잘못된_JSON은_표준_검증_오류로_거부되고_서비스를_호출하지_않는다() throws Exception {
        mockMvc.perform(put(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400));

        then(pushDeviceTokenService).shouldHaveNoInteractions();
    }

    @Test
    void 동시_등록_충돌은_표준_409_오류로_반환된다() throws Exception {
        willThrow(new PushDeviceTokenException(PushDeviceTokenErrorCode.TOKEN_REGISTRATION_CONFLICT))
                .given(pushDeviceTokenService)
                .upsert(USER_ID, "android-token", PushPlatform.ANDROID);

        mockMvc.perform(put(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"android-token\",\"platform\":\"ANDROID\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409));
    }
}
