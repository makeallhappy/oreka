package net.sf.oreka.spring.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA 前端路由回退
 *
 * Vue Router 用 history 模式,前端路由(/users、/programs、/segments/:id 等)
 * 在服务端没有对应资源,刷新或直接访问会 404。此 controller 把这些路径
 * forward 到 index.html,由 Vue Router 在客户端接管。
 *
 * 明确列出前端路由,不用通配符,避免误接管 /api/**、/command、/ws/** 等后端端点。
 * (静态资源 index.html/assets 由 Spring Boot 默认 classpath:/static 映射处理)
 */
@Controller
public class SpaForwardController {

    /**
     * 前端路由 -> index.html
     * 新增前端页面时在此补充路径。带点的路径(文件扩展名)不会匹配,不影响静态资源。
     */
    @GetMapping({
            "/login",
            "/users",
            "/programs",
            "/services",
            "/segments",
            "/segments/{id:\\d+}",
    })
    public String forwardToSpa() {
        return "forward:/index.html";
    }
}
