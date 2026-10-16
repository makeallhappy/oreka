package net.sf.oreka.spring.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SPA 配置 —— 静态资源由 Spring Boot 默认映射(classpath:/static)处理,
 * 不在此注册 /** resource handler,避免接管 /api/** 等 controller 路径。
 *
 * 前端路由回退由 SpaForwardController 负责(见该类)。
 * 本类仅作标记,保留以便后续添加 MVC 配置。
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {
}
