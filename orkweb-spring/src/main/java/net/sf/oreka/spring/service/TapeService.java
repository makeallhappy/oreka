package net.sf.oreka.spring.service;

import net.sf.oreka.Direction;
import net.sf.oreka.persistent.OrkService;
import net.sf.oreka.persistent.OrkTape;
import net.sf.oreka.spring.repository.ServiceRepository;
import net.sf.oreka.spring.repository.TapeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Map;
import java.util.Optional;

/**
 * Tape 业务逻辑 —— 迁移自 orktrack TapeManager.notifyTapeMessage
 *
 * 核心职责:
 * 1. START 消息 → 新增 OrkTape
 * 2. STOP 消息 → 匹配运行中的 tape(nativeCallId + 时间窗),更新 duration/filename/state
 * 3. tape 合并:同 nativeCallId 的 START+STOP 不重复建记录
 *
 * 返回值语义(沿用老代码):true=保留 tape,false=请求 orkaudio 删除(deletetape=true)
 */
@Service
@Transactional
public class TapeService {

    private static final Logger log = LoggerFactory.getLogger(TapeService.class);

    /** 运行中 tape 匹配的时间窗(秒),沿用老代码逻辑 */
    private static final int RUNNING_TAPE_TIME_WINDOW_SEC = 3600;

    private final TapeRepository tapeRepository;
    private final ServiceRepository serviceRepository;
    private final LiveCallRegistry liveCallRegistry;

    public TapeService(TapeRepository tapeRepository, ServiceRepository serviceRepository,
                       LiveCallRegistry liveCallRegistry) {
        this.tapeRepository = tapeRepository;
        this.serviceRepository = serviceRepository;
        this.liveCallRegistry = liveCallRegistry;
    }

    /**
     * 处理 orkaudio 的 TapeMessage
     * @param params 协议参数(全小写 key)
     * @return true=保留 tape,false=请求删除
     */
    public boolean notifyTapeMessage(Map<String, String> params) {
        String stage = params.getOrDefault("stage", "UNKN").toUpperCase();
        String nativeCallId = params.get("nativecallid");
        String recId = params.getOrDefault("recid", "");

        log.info("TapeMessage: stage={} recId={} nativeCallId={}", stage, recId, nativeCallId);

        OrkService service = retrieveOrCreateService(
                params.getOrDefault("service", "unknown"),
                params.getOrDefault("hostname", ""));

        switch (stage) {
            case "START":
                return handleStart(params, service);
            case "STOP":
                return handleStop(params, service);
            case "READY":
            case "COMPLETE":
                // READY/COMPLETE 老代码里是元数据更新,暂按保留处理
                log.debug("Stage {} received, treating as metadata update", stage);
                return true;
            default:
                log.warn("Unknown stage: {}", stage);
                return true;
        }
    }

    private boolean handleStart(Map<String, String> params, OrkService service) {
        String nativeCallId = params.get("nativecallid");

        // 检查是否已有运行中的 tape(orkaudio 重连场景)
        Optional<OrkTape> existing = findRunningTape(nativeCallId, params.get("captureport"));
        if (existing.isPresent()) {
            log.info("START for existing running tape id={}, ignoring duplicate", existing.get().getId());
            return true;
        }

        OrkTape tape = new OrkTape();
        tape.setTimestamp(parseTimestamp(params.get("timestamp")));
        tape.setDuration(parseLong(params.get("duration")));
        tape.setFilename(params.getOrDefault("filename", ""));
        tape.setLocalParty(params.getOrDefault("localparty", ""));
        tape.setLocalEntryPoint(params.getOrDefault("localentrypoint", ""));
        tape.setRemoteParty(params.getOrDefault("remoteparty", ""));
        tape.setDirection(parseDirection(params.get("direction")));
        tape.setPortName(params.getOrDefault("captureport", ""));
        tape.setNativeCallId(nativeCallId);
        tape.setState("START");
        tape.setService(service);
        tape.setExpiryTimestamp(new Date(0));

        tapeRepository.save(tape);
        log.info("Created tape id={} for nativeCallId={}", tape.getId(), nativeCallId);
        liveCallRegistry.onCallStart(params);
        return true;
    }

    private boolean handleStop(Map<String, String> params, OrkService service) {
        String nativeCallId = params.get("nativecallid");
        Optional<OrkTape> existing = findRunningTape(nativeCallId, params.get("captureport"));

        if (existing.isEmpty()) {
            log.warn("STOP for unknown tape nativeCallId={}, creating orphan record", nativeCallId);
            // 老代码也会建孤儿记录(orkaudio 重启后 START 丢了)
            OrkTape tape = new OrkTape();
            tape.setTimestamp(parseTimestamp(params.get("timestamp")));
            tape.setNativeCallId(nativeCallId);
            tape.setPortName(params.getOrDefault("captureport", ""));
            tape.setService(service);
            tape.setState("STOP");
            tape.setDuration(parseLong(params.get("duration")));
            tape.setFilename(params.getOrDefault("filename", ""));
            tape.setExpiryTimestamp(new Date(0));
            tapeRepository.save(tape);
            return true;
        }

        OrkTape tape = existing.get();
        tape.setState("STOP");
        tape.setDuration(parseLong(params.get("duration")));
        String filename = params.get("filename");
        if (filename != null && !filename.isBlank()) {
            tape.setFilename(filename);
        }
        tapeRepository.save(tape);
        log.info("Updated tape id={} to STOP, duration={}", tape.getId(), tape.getDuration());
        liveCallRegistry.onCallEnd(params);
        return true;
    }

    /**
     * 查找运行中的 tape(nativeCallId + 时间窗内)
     * 迁移自 TapeManager.getBestMatchingRunningTape
     */
    private Optional<OrkTape> findRunningTape(String nativeCallId, String portName) {
        if (nativeCallId == null || nativeCallId.isBlank()) {
            return Optional.empty();
        }
        // 简化版:按 nativeCallId 找最新的,且 state 不是 STOP
        // 完整版(M2 后期)可加 portName + 时间窗过滤
        return tapeRepository.findFirstByNativeCallIdOrderByIdDesc(nativeCallId)
                .filter(t -> !"STOP".equals(t.getState()));
    }

    /**
     * OrkService retrieveOrCreate —— 迁移自 ServiceManager
     */
    private OrkService retrieveOrCreateService(String name, String hostname) {
        return serviceRepository.findByName(name)
                .orElseGet(() -> {
                    OrkService s = new OrkService();
                    s.setName(name);
                    s.setHostname(hostname);
                    s.setRecordMaster(true);
                    log.info("Auto-created OrkService: {}@{}", name, hostname);
                    return serviceRepository.save(s);
                });
    }

    private Date parseTimestamp(String ts) {
        try {
            return ts != null ? new Date(Long.parseLong(ts) * 1000) : new Date();
        } catch (NumberFormatException e) {
            return new Date();
        }
    }

    private long parseLong(String s) {
        try {
            return s != null ? Long.parseLong(s) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Direction parseDirection(String d) {
        if (d == null) return Direction.UNKN;
        try {
            return Direction.valueOf(d.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Direction.UNKN;
        }
    }
}
