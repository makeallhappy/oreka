package net.sf.oreka.spring.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.sf.oreka.spring.service.LiveCallRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实时通话 WebSocket 处理器
 *
 * 连接建立:推送当前全部进行中通话(初始化)
 * 状态变化:LiveCallRegistry 回调,广播给所有连接的客户端
 * 消息格式:{ "type": "liveCalls", "calls": [...] }
 */
@Component
public class LiveCallWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(LiveCallWebSocketHandler.class);

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final LiveCallRegistry registry;
    private final ObjectMapper mapper;

    public LiveCallWebSocketHandler(LiveCallRegistry registry) {
        this.registry = registry;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 注册为状态变化监听器:通话 START/STOP 时广播
        this.registry.setListener(this::broadcast);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        log.info("WebSocket connected: {} (total={})", session.getId(), sessions.size());
        // 新客户端先推一次全量
        sendTo(session, registry.getLiveCalls());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("WebSocket disconnected: {} (total={})", session.getId(), sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WebSocket transport error: {}", session.getId(), exception);
        sessions.remove(session);
    }

    /** 广播给所有连接的客户端(LiveCallRegistry 回调) */
    private void broadcast(Collection<LiveCallRegistry.LiveCall> calls) {
        sessions.removeIf(s -> !s.isOpen());
        for (WebSocketSession session : sessions) {
            try {
                sendTo(session, calls);
            } catch (Exception e) {
                log.warn("Failed to send to session {}", session.getId(), e);
                sessions.remove(session);
            }
        }
    }

    private void sendTo(WebSocketSession session, Collection<LiveCallRegistry.LiveCall> calls) throws Exception {
        if (!session.isOpen()) return;
        String payload = mapper.writeValueAsString(Map.of(
                "type", "liveCalls",
                "calls", calls,
                "count", calls.size()
        ));
        synchronized (session) {
            session.sendMessage(new TextMessage(payload));
        }
    }
}
