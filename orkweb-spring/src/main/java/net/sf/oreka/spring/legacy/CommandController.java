package net.sf.oreka.spring.legacy;

import net.sf.oreka.spring.service.MetadataService;
import net.sf.oreka.spring.service.TapeService;
import net.sf.oreka.spring.service.UserStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * orkaudio 协议兼容端点 —— 替代老 orktrack 的 CommandServlet
 *
 * 协议(从 orktrack 反推):
 *   请求:GET/POST /command?message=tape&stage=START&recid=xxx&...(参数名全小写)
 *   响应:key=value 单行文本(success=true deletetape=false comment=),Content-Type: text/plain
 *
 * 容错原则(沿用老代码):任何异常都返回 200 + success=false,不返回 4xx/5xx,
 * 避免 orkaudio 重传风暴。
 */
@RestController
public class CommandController {

    private static final Logger log = LoggerFactory.getLogger(CommandController.class);

    private final TapeService tapeService;
    private final MetadataService metadataService;
    private final UserStateService userStateService;

    public CommandController(TapeService tapeService, MetadataService metadataService,
                             UserStateService userStateService) {
        this.tapeService = tapeService;
        this.metadataService = metadataService;
        this.userStateService = userStateService;
    }

    @GetMapping(value = "/command", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> commandGet(@RequestParam Map<String, String> params) {
        return handle(params);
    }

    @PostMapping(value = "/command", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> commandPost(@RequestParam Map<String, String> params) {
        return handle(params);
    }

    private ResponseEntity<String> handle(Map<String, String> params) {
        // 参数名统一转小写(协议要求)
        Map<String, String> lower = new java.util.HashMap<>();
        params.forEach((k, v) -> lower.put(k.toLowerCase(), v));

        String message = lower.getOrDefault("message", "").toLowerCase();
        log.debug("Command: message={} params={}", message, lower);

        try {
            return switch (message) {
                case "tape" -> handleTape(lower);
                case "ping" -> ok(simpleResponse(true, false, "pong"));
                case "init" -> ok(simpleResponse(true, false, ""));
                case "metadata" -> handleMetadata(lower);
                case "userstate" -> handleUserState(lower);
                case "configurelog" -> ok(simpleResponse(true, false, "")); // 老协议里是日志配置,直接 ack
                default -> {
                    log.warn("Unknown message type: {}", message);
                    yield ok(simpleResponse(false, false, "unknown message: " + message));
                }
            };
        } catch (Exception e) {
            log.error("Command processing error", e);
            return ok(simpleResponse(false, false, e.getMessage()));
        }
    }

    private ResponseEntity<String> handleTape(Map<String, String> params) {
        boolean keep = tapeService.notifyTapeMessage(params);
        // TapeResponse: success + deletetape + comment
        return ok(simpleResponse(true, !keep, keep ? "" : "delete requested"));
    }

    private ResponseEntity<String> handleMetadata(Map<String, String> params) {
        String error = metadataService.processMetadata(params);
        return ok(simpleResponse(error == null, false, error == null ? "" : error));
    }

    private ResponseEntity<String> handleUserState(Map<String, String> params) {
        String error = userStateService.processUserState(params);
        return ok(simpleResponse(error == null, false, error == null ? "" : error));
    }

    /**
     * key=value 单行响应(协议格式,一个字不能改)
     * 格式:success=true deletetape=false comment=xxx
     */
    private String simpleResponse(boolean success, boolean deleteTape, String comment) {
        StringBuilder sb = new StringBuilder();
        sb.append("success=").append(success);
        sb.append(" deletetape=").append(deleteTape);
        sb.append(" comment=").append(comment == null ? "" : comment.replace("\n", " "));
        return sb.toString();
    }

    private ResponseEntity<String> ok(String body) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(body);
    }
}
