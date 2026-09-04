package com.highpass.runspot.notification.push.api;

import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.highpass.runspot.common.config.SecurityConfig;
import com.highpass.runspot.common.exception.handler.ApiExceptionHandler;
import com.highpass.runspot.common.jwt.JwtAuthenticationFilter;
import com.highpass.runspot.common.jwt.JwtProvider;
import com.highpass.runspot.notification.push.service.PushDeviceTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PushTokenController.class)
@AutoConfigureMockMvc
@Import({ApiExceptionHandler.class, JwtAuthenticationFilter.class, SecurityConfig.class})
class PushTokenAuthenticationTest {

    private static final String BASE_URL = "/users/me/push-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PushDeviceTokenService pushDeviceTokenService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @Test
    void 인증되지_않은_토큰_등록과_삭제는_기존_JSON_401_계약으로_거부된다() throws Exception {
        mockMvc.perform(put(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"android-token\",\"platform\":\"ANDROID\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
        mockMvc.perform(delete(BASE_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));

        then(pushDeviceTokenService).shouldHaveNoInteractions();
    }
}
