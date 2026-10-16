package net.sf.oreka.spring.service;

import net.sf.oreka.Direction;
import net.sf.oreka.persistent.OrkSegment;
import net.sf.oreka.persistent.OrkService;
import net.sf.oreka.persistent.OrkTape;
import net.sf.oreka.persistent.OrkUser;
import net.sf.oreka.spring.repository.LoginStringRepository;
import net.sf.oreka.spring.repository.SegmentRepository;
import net.sf.oreka.spring.repository.ServiceRepository;
import net.sf.oreka.spring.repository.TapeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Metadata 消息处理 —— 迁移自 orktrack MetadataMessage + Port.notifyMetadataMessage
 *
 * metadata 是录音段(OrkSegment)的真正生成源:
 *   stage=START    -> 暂存开始时间(按 capturePort)
 *   stage=STOP     -> 与暂存的 START 配对,算 duration,生成 segment
 *   stage=COMPLETE -> 直接带 duration,生成 segment
 *
 * 生成时:按 localParty 反查 user(OrkLoginString 关联)、关联运行中 tape、落库。
 * Program 过滤当前按"全部保留"(active program 过滤留作后续)。
 */
@Service
@Transactional
public class MetadataService {

    private static final Logger log = LoggerFactory.getLogger(MetadataService.class);

    private final ServiceRepository serviceRepository;
    private final SegmentRepository segmentRepository;
    private final TapeRepository tapeRepository;
    private final LoginStringRepository loginStringRepository;
    private final ProgramFilterService programFilterService;

    /** 按 capturePort 暂存 metadata START(等 STOP 配对) */
    private final Map<String, PendingStart> pendingStarts = new ConcurrentHashMap<>();

    public MetadataService(ServiceRepository serviceRepository,
                           SegmentRepository segmentRepository,
                           TapeRepository tapeRepository,
                           LoginStringRepository loginStringRepository,
                           ProgramFilterService programFilterService) {
        this.serviceRepository = serviceRepository;
        this.segmentRepository = segmentRepository;
        this.tapeRepository = tapeRepository;
        this.loginStringRepository = loginStringRepository;
        this.programFilterService = programFilterService;
    }

    /**
     * @return null=成功,否则=错误 comment
     */
    public String processMetadata(Map<String, String> params) {
        String serviceName = params.getOrDefault("service", "");
        String capturePort = params.getOrDefault("captureport", "");
        String stage = params.getOrDefault("stage", "UNKN").toUpperCase();

        log.info("MetadataMessage: service={} port={} stage={} local={} remote={} direction={}",
                serviceName, capturePort, stage,
                params.get("localparty"), params.get("remoteparty"), params.get("direction"));

        // 老代码逻辑:service 必须已存在(不自动创建)
        Optional<OrkService> service = serviceRepository.findByName(serviceName);
        if (service.isEmpty()) {
            String comment = "service:" + serviceName + " does not exist";
            log.error("MetadataMessage: {}", comment);
            return comment;
        }
        if (capturePort.isBlank()) {
            return "captureport is required";
        }

        switch (stage) {
            case "START" -> onStart(capturePort, params);
            case "STOP" -> onStop(capturePort, params, service.get());
            case "COMPLETE" -> onComplete(capturePort, params, service.get());
            default -> log.debug("Metadata stage {} ignored", stage);
        }
        return null;
    }

    /** START:暂存开始时间和元数据 */
    private void onStart(String capturePort, Map<String, String> params) {
        pendingStarts.put(capturePort, new PendingStart(
                parseTimestamp(params.get("timestamp")),
                params.getOrDefault("localparty", ""),
                params.getOrDefault("localentrypoint", ""),
                params.getOrDefault("remoteparty", ""),
                parseDirection(params.get("direction"))
        ));
        log.debug("Metadata START cached for port {}", capturePort);
    }

    /** STOP:与暂存的 START 配对,算 duration,生成 segment */
    private void onStop(String capturePort, Map<String, String> params, OrkService service) {
        PendingStart start = pendingStarts.remove(capturePort);
        if (start == null) {
            log.warn("Metadata STOP without prior START on port {}, ignored", capturePort);
            return;
        }
        long stopTs = parseTimestamp(params.get("timestamp"));
        long durationSec = Math.max(0, (stopTs - start.epochSec()));
        Date startDate = new Date(start.epochSec() * 1000);
        createSegment(startDate, durationSec, start.localParty(), start.localEntryPoint(),
                start.remoteParty(), start.direction(), capturePort, service);
    }

    /** COMPLETE:消息自带 duration,直接生成 segment */
    private void onComplete(String capturePort, Map<String, String> params, OrkService service) {
        long ts = parseTimestamp(params.get("timestamp"));
        long durationSec = parseLong(params.get("duration"));
        Date startDate = new Date(ts * 1000);
        createSegment(startDate, durationSec,
                params.getOrDefault("localparty", ""),
                params.getOrDefault("localentrypoint", ""),
                params.getOrDefault("remoteparty", ""),
                parseDirection(params.get("direction")),
                capturePort, service);
    }

    /** 生成 segment:反查 user、关联运行中 tape、落库 */
    private void createSegment(Date startDate, long durationSec, String localParty,
                               String localEntryPoint, String remoteParty, Direction direction,
                               String capturePort, OrkService service) {
        OrkSegment segment = new OrkSegment();
        segment.setTimestamp(startDate);
        segment.setDuration(durationSec);
        segment.setLocalParty(localParty);
        segment.setLocalEntryPoint(localEntryPoint);
        segment.setRemoteParty(remoteParty);
        segment.setDirection(direction);
        segment.setSessionOffset(0);
        segment.setPortName(capturePort);
        segment.setLoginString(localParty);

        // 按 localParty 反查 user(OrkLoginString 关联)
        if (localParty != null && !localParty.isBlank()) {
            OrkUser user = loginStringRepository.findFirstByLoginStringOrderByIdAsc(localParty)
                    .map(net.sf.oreka.persistent.OrkLoginString::getUser).orElse(null);
            segment.setUser(user);
        }

        // 关联运行中 tape(同 port 最新非 STOP)
        Optional<OrkTape> tape = tapeRepository.findFirstByPortNameAndStateNotOrderByIdDesc(capturePort, "STOP");
        tape.ifPresent(segment::setTape);

        // Program 过滤(老代码 filterSegmentAgainstAllPrograms):无计划全录,有计划需命中一个
        if (!programFilterService.shouldKeep(segment)) {
            log.info("#{}: segment dropped by program filter {}->{}", capturePort, localParty, remoteParty);
            return;
        }

        segmentRepository.save(segment);
        log.info("#{}: written segment id={} dur={}s {}->{} tape={}",
                capturePort, segment.getId(), durationSec, localParty, remoteParty,
                tape.map(OrkTape::getId).orElse(null));
    }

    private long parseTimestamp(String ts) {
        try {
            return ts != null ? Long.parseLong(ts) : System.currentTimeMillis() / 1000;
        } catch (NumberFormatException e) {
            return System.currentTimeMillis() / 1000;
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

    /** 暂存的 START 元数据(等 STOP 配对) */
    private record PendingStart(long epochSec, String localParty, String localEntryPoint,
                                String remoteParty, Direction direction) {
    }
}
