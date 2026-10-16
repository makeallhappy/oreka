package net.sf.oreka.spring.service;

import net.sf.oreka.Direction;
import net.sf.oreka.persistent.OrkService;
import net.sf.oreka.spring.repository.ServiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * Metadata 消息处理 —— 迁移自 orktrack MetadataMessage
 *
 * 用途:orkaudio 在录音过程中上报元数据(主被叫/方向/端口等),
 * 更新到对应 session 的 segment 上。老代码走 Port.notifyMetadataMessage,
 * 本质是更新"当前正在录音"的 segment 元数据。
 *
 * 简化版(M2):只做 service 校验 + 记录日志,完整 segment 更新逻辑在 M3 补
 * (需要关联"运行中 session",当前 findRunningTape 是简化版)。
 */
@Service
@Transactional
public class MetadataService {

    private static final Logger log = LoggerFactory.getLogger(MetadataService.class);

    private final ServiceRepository serviceRepository;

    public MetadataService(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    /**
     * @return null=成功,否则=错误 comment
     */
    public String processMetadata(Map<String, String> params) {
        String serviceName = params.getOrDefault("service", "");
        String capturePort = params.getOrDefault("captureport", "");
        String localParty = params.getOrDefault("localparty", "");
        String remoteParty = params.getOrDefault("remoteparty", "");
        String direction = params.getOrDefault("direction", "UNKN");
        String stage = params.getOrDefault("stage", "UNKN");

        log.info("MetadataMessage: service={} port={} local={} remote={} direction={} stage={}",
                serviceName, capturePort, localParty, remoteParty, direction, stage);

        // 老代码逻辑:service 必须已存在(不自动创建)
        Optional<OrkService> service = serviceRepository.findByName(serviceName);
        if (service.isEmpty()) {
            String comment = "service:" + serviceName + " does not exist";
            log.error("MetadataMessage: {}", comment);
            return comment;
        }

        // TODO(M3): 更新运行中 session 的 segment 元数据
        // 需要:1) 找到 capturePort 对应的运行中 session
        //       2) 更新该 session 最新 segment 的 localParty/remoteParty/direction
        // 当前 M2 先记录日志,返回成功(service 存在即认为元数据被接受)
        log.debug("Metadata accepted for service={} port={} (segment update pending M3)",
                serviceName, capturePort);
        return null;
    }
}
