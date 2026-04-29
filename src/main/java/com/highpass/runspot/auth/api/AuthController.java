package com.highpass.runspot.auth.api;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.auth.service.AuthService;
import com.highpass.runspot.auth.service.dto.request.LoginRequest;
import com.highpass.runspot.auth.service.dto.request.RefreshTokenRequest;
import com.highpass.runspot.auth.service.dto.request.SignupRequest;
import com.highpass.runspot.auth.service.dto.response.SignupResponse;
import com.highpass.runspot.auth.service.dto.response.TokenResponse;
import com.highpass.runspot.common.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "회원가입", description = "회원가입 기능입니다.")
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest request) {
        try {
            User user = authService.signup(request);
            log.info("User created successfully: {}", user.getId());

            SignupResponse response = SignupResponse.from(user);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            log.error("Signup failed", e);

            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getClass().getSimpleName());
            error.put("message", e.getMessage());

            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            error.put("stackTrace", sw.toString());

            if (e.getCause() != null) {
                error.put("cause", e.getCause().toString());
            }

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @Operation(summary = "로그인", description = "로그인 후 액세스 토큰과 리프레시 토큰을 반환합니다.")
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            TokenResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Login failed", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getClass().getSimpleName());
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    @Operation(summary = "토큰 재발급", description = "리프레시 토큰으로 액세스 토큰과 리프레시 토큰을 재발급합니다.")
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        try {
            TokenResponse response = authService.refresh(request.getRefreshToken());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Token refresh failed", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getClass().getSimpleName());
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }
    }

    @Operation(summary = "로그아웃", description = "리프레시 토큰을 무효화합니다.")
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        Map<String, String> response = new HashMap<>();
        response.put("message", "로그아웃 성공");
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "회원탈퇴", description = "현재 로그인 된 사용자를 탈퇴 처리합니다.")
    @DeleteMapping("/withdraw")
    public ResponseEntity<Map<String, String>> withdraw(
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Unauthorized");
            error.put("message", "로그인이 필요합니다");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body((Map) error);
        }
        authService.withdraw(principal.getId());
        Map<String, String> response = new HashMap<>();
        response.put("message", "회원 탈퇴 처리됨");
        return ResponseEntity.ok(response);
    }
}
