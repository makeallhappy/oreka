package net.sf.oreka.spring.config;

import net.sf.oreka.spring.websocket.LiveCallWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置 —— 实时通话状态推送
 *
 * 端点:/ws/live-calls
 * 前端连接后:先收一次全量进行中通话,之后每次状态变化实时推送增量全量。
 *
 * 协议:JSON 文本帧
 *   { "type": "liveCalls", "calls": [ {callId, localParty, remoteParty, ...} ] }
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final LiveCallWebSocketHandler liveCallWebSocketHandler;

    public WebSocketConfig(LiveCallWebSocketHandler liveCallWebSocketHandler) {
        this.liveCallWebSocketHandler = liveCallWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(liveCallWebSocketHandler, "/ws/live-calls")
                // 开发期放开跨域(前端 :5173 代理);生产按域名收紧
                .setAllowedOrigins("*");
    }
}
