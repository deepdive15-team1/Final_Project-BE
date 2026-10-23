package com.highpass.runspot.chat.config;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.highpass.runspot.common.config.SecurityConfig;
import com.highpass.runspot.common.jwt.JwtAuthenticationFilter;
import com.highpass.runspot.common.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@SpringJUnitConfig(WebSocketSecurityTest.Config.class)
@WebAppConfiguration
class WebSocketSecurityTest {
    private static final String CLIENT_ORIGIN = "https://chat-client.example.com";

    @Autowired private WebApplicationContext context;
    @Autowired private FilterChainProxy securityFilterChain;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(securityFilterChain).build();
    }

    @Test
    void handshakePassesSecurityWithoutHttpJwtFromAnotherOrigin() throws Exception {
        mvc.perform(get("/ws-stomp")
                        .header("Origin", CLIENT_ORIGIN)
                        .header("Connection", "Upgrade")
                        .header("Upgrade", "websocket"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", CLIENT_ORIGIN));
    }

    @Test
    void nativeClientWithoutOriginPassesSecurity() throws Exception {
        mvc.perform(get("/ws-stomp")).andExpect(status().isOk());
    }

    @Test
    void restCorsRemainsRestricted() throws Exception {
        mvc.perform(get("/health").header("Origin", CLIENT_ORIGIN))
                .andExpect(status().isForbidden());
    }

    @Test
    void privateRestStillRequiresAuthentication() throws Exception {
        mvc.perform(get("/private")).andExpect(status().isUnauthorized());
    }

    @Configuration
    @EnableWebMvc
    @Import(SecurityConfig.class)
    static class Config {
        @Bean
        static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            return new JwtAuthenticationFilter(mock(JwtProvider.class));
        }

        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }
    }

    // A probe isolates the HTTP security/CORS gate from the WebSocket container.
    @RestController
    static class ProbeController {
        @GetMapping({"/ws-stomp", "/health", "/private"})
        String probe() {
            return "ok";
        }
    }
}
