package net.sf.oreka.spring.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实时通话状态注册表 —— 跟踪"正在录音中"的通话
 *
 * 数据源:orkaudio 的 tape 消息
 *   stage=START -> 加入进行中通话
 *   stage=STOP/COMPLETE -> 移除
 *
 * 状态变化时回调监听器(由 WebSocket 层注册,广播给前端)。
 * 纯内存,不落库;服务重启后由后续 tape 消息自然重建。
 */
@Service
public class LiveCallRegistry {

    private static final Logger log = LoggerFactory.getLogger(LiveCallRegistry.class);

    /** 进行中通话,key = nativeCallId(或 recId 兜底) */
    private final Map<String, LiveCall> liveCalls = new ConcurrentHashMap<>();

    /** 状态变化监听器(WebSocket 层注册) */
    private volatile LiveCallListener listener;

    public void setListener(LiveCallListener listener) {
        this.listener = listener;
    }

    /**
     * 由 TapeService 在 tape START 时调用
     */
    public void onCallStart(Map<String, String> params) {
        String key = key(params);
        LiveCall call = new LiveCall(
                key,
                params.getOrDefault("localparty", ""),
                params.getOrDefault("remoteparty", ""),
                params.getOrDefault("direction", "UNKN"),
                params.getOrDefault("captureport", ""),
                params.getOrDefault("service", ""),
                Instant.now()
        );
        liveCalls.put(key, call);
        log.info("Live call started: {} {}->{}", key, call.localParty(), call.remoteParty());
        notifyChange();
    }

    /**
     * 由 TapeService 在 tape STOP/COMPLETE 时调用
     */
    public void onCallEnd(Map<String, String> params) {
        String key = key(params);
        LiveCall removed = liveCalls.remove(key);
        if (removed != null) {
            log.info("Live call ended: {}", key);
            notifyChange();
        }
    }

    /**
     * 当前所有进行中通话(供新连上的 WebSocket 客户端初始化)
     */
    public Collection<LiveCall> getLiveCalls() {
        return liveCalls.values();
    }

    private String key(Map<String, String> params) {
        String nativeCallId = params.get("nativecallid");
        if (nativeCallId != null && !nativeCallId.isBlank()) return nativeCallId;
        return params.getOrDefault("recid", "unknown-" + params.hashCode());
    }

    private void notifyChange() {
        LiveCallListener l = listener;
        if (l != null) {
            try {
                l.onLiveCallsChanged(getLiveCalls());
            } catch (Exception e) {
                log.warn("Live call listener error", e);
            }
        }
    }

    /** 进行中通话的实时视图(不暴露内部实体,纯 DTO) */
    public record LiveCall(
            String callId,
            String localParty,
            String remoteParty,
            String direction,
            String capturePort,
            String service,
            Instant startedAt
    ) {
    }

    @FunctionalInterface
    public interface LiveCallListener {
        void onLiveCallsChanged(Collection<LiveCall> liveCalls);
    }
}
