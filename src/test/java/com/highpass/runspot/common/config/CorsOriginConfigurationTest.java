package com.highpass.runspot.common.config;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/** 프론트 배포 주소를 재배포 없이 바꿀 수 있어야 하므로 허용 Origin이 설정으로 주입되는지 확인한다. */
@SpringJUnitConfig(CorsOriginConfigurationTest.Config.class)
@WebAppConfiguration
@TestPropertySource(properties =
        "app.cors.allowed-origin-patterns=https://preview.example.com,https://*.vercel.app")
class CorsOriginConfigurationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private FilterChainProxy securityFilterChain;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilterChain).build();
    }

    @Test
    void 설정에_나열한_origin은_허용된다() throws Exception {
        mvc.perform(get("/health").header("Origin", "https://preview.example.com"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://preview.example.com"));
    }

    @Test
    void 와일드카드_패턴은_하위_도메인에도_적용된다() throws Exception {
        mvc.perform(get("/health").header("Origin", "https://runspot-one-git-dev.vercel.app"))
                .andExpect(status().isOk());
    }

    @Test
    void 설정에_없는_origin은_차단된다() throws Exception {
        mvc.perform(get("/health").header("Origin", "https://evil.example.com"))
                .andExpect(status().isForbidden());
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

    @RestController
    static class ProbeController {
        @GetMapping("/health")
        String probe() {
            return "ok";
        }
    }
}
