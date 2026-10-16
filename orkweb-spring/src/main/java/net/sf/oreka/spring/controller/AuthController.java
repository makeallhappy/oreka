package net.sf.oreka.spring.controller;

import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import net.sf.oreka.persistent.OrkUser;
import net.sf.oreka.spring.config.JwtTokenProvider;
import net.sf.oreka.spring.dto.LoginRequest;
import net.sf.oreka.spring.dto.LoginResponse;
import net.sf.oreka.spring.dto.RefreshRequest;
import net.sf.oreka.spring.dto.UserDto;
import net.sf.oreka.spring.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "JWT 认证")
public class AuthController {

    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(UserService userService, JwtTokenProvider jwtTokenProvider) {
        this.userService = userService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping("/login")
    @Operation(summary = "登录", description = "loginString + password → JWT;老明文密码验证成功后自动升级 BCrypt")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        Optional<OrkUser> userOpt = userService.authenticate(req.loginString(), req.password());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        OrkUser user = userOpt.get();
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), req.loginString());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        return ResponseEntity.ok(new LoginResponse(
                accessToken,
                refreshToken,
                "Bearer",
                120 * 60,
                UserDto.from(user)
        ));
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新 token")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        if (!jwtTokenProvider.isValid(req.refreshToken())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Claims claims = jwtTokenProvider.parse(req.refreshToken());
        if (!"refresh".equals(claims.get("type", String.class))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        int userId = jwtTokenProvider.getUserId(claims);
        Optional<UserDto> userOpt = userService.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // 用 loginString 重新查(拿到完整 OrkUser 生成新 token)
        // 简化:loginString 从 user 反查太绕,直接用 userId 生成,loginString 置空由前端重新 login
        String accessToken = jwtTokenProvider.generateAccessToken(userId, "");
        return ResponseEntity.ok(new LoginResponse(
                accessToken,
                req.refreshToken(),
                "Bearer",
                120 * 60,
                userOpt.get()
        ));
    }
}
