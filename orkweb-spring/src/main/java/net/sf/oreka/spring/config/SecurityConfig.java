package net.sf.oreka.spring.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置 —— JWT 无状态认证
 *
 * 放行:/api/v1/auth/** (登录/刷新)、/command (orkaudio 上报,用 legacy 兼容)、
 *       swagger、actuator health
 * 其余:需 JWT
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 认证端点放行
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/api/v1/captcha", "/api/v1/captcha/**").permitAll()
                // orkaudio 上报端点放行(legacy 协议,无 JWT;生产环境建议网络层隔离)
                // /orktrack/command 别名:兼容老 orkaudio TrackerServicename 拼接的路径
                .requestMatchers("/command", "/orktrack/command").permitAll()
                // WebSocket 实时通话状态(开发期放开;生产按网络层隔离)
                .requestMatchers("/ws/**").permitAll()
                // swagger / openapi
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // actuator
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // 其余 API 全部需认证
                .requestMatchers("/api/**").authenticated()
                // 非 API 路径(SPA 静态资源、前端路由)放行 —— 页面本身不涉密,
                // 数据全靠 JS 带 JWT 调 /api,未认证会被 401 导到登录页
                .anyRequest().permitAll()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
