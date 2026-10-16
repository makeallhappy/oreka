package net.sf.oreka.spring.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Port→User 运行时绑定注册表 —— 对齐老 orktrack PortManager 的内存设计
 *
 * 老代码里 port→user 绑定是 Port 对象的内存状态(不落库),
 * 由 UserStateMessage LOGIN/LOGOUT 驱动。metadata/segment 更新时
 * 需要按 capturePort 反查"当前哪个坐席在这个分机上"。
 *
 * 纯内存 ConcurrentHashMap,服务重启后由后续 LOGIN 消息自然重建。
 */
@Service
public class PortUserRegistry {

    private static final Logger log = LoggerFactory.getLogger(PortUserRegistry.class);

    /** key = capturePort(如 SIP/1001),value = 绑定信息 */
    private final Map<String, PortBinding> bindings = new ConcurrentHashMap<>();

    /** 坐席 LOGIN 到某 port */
    public void bind(String capturePort, int userId, String loginString) {
        if (capturePort == null || capturePort.isBlank()) return;
        bindings.put(capturePort, new PortBinding(userId, loginString, Instant.now()));
        log.info("Port bound: {} -> user {} ({})", capturePort, userId, loginString);
    }

    /** 坐席 LOGOUT,解绑 */
    public void unbind(String capturePort) {
        if (capturePort == null || capturePort.isBlank()) return;
        PortBinding removed = bindings.remove(capturePort);
        if (removed != null) {
            log.info("Port unbound: {} (was user {})", capturePort, removed.userId());
        }
    }

    /** 按 port 查当前绑定的坐席(metadata/segment 更新用) */
    public Optional<PortBinding> lookup(String capturePort) {
        return Optional.ofNullable(bindings.get(capturePort));
    }

    /** 当前所有绑定(监控/调试用) */
    public Map<String, PortBinding> all() {
        return Map.copyOf(bindings);
    }

    public record PortBinding(int userId, String loginString, Instant boundAt) {
    }
}
