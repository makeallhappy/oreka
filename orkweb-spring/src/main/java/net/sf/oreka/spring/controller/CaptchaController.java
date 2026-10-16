package net.sf.oreka.spring.controller;

import com.wf.captcha.SpecCaptcha;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录验证码 —— 用 easy-captcha(com.github.whvcse:easy-captcha)生成算术验证码
 *
 * 流程:
 *  1. GET /api/v1/captcha -> 生成算术验证码图,返回 {captchaId, imageBase64};答案暂存内存(带过期)
 *  2. 前端登录时把 captchaId + 用户输入的 captchaCode 一起 POST 到 /auth/login
 *  3. AuthController 调 verify(captchaId, captchaCode) 校验,一次性(验证后即删除)
 */
@RestController
@RequestMapping("/api/v1/captcha")
public class CaptchaController {

    private static final Logger log = LoggerFactory.getLogger(CaptchaController.class);

    /** 验证码有效期(ms),默认 5 分钟 */
    private static final long EXPIRE_MS = 5 * 60 * 1000L;

    private record Entry(String answer, long expiresAt) {}

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    /**
     * 生成一个字符验证码
     * 返回 captchaId(一次性 token) + 图片 base64
     *
     * 注:用 SpecCaptcha(字符型)而非 ArithmeticCaptcha —— 后者依赖 javax.script
     *     Nashorn 引擎算算术答案,而 JDK 15+ 已移除 Nashorn(engine=null → NPE)。
     */
    @GetMapping
    public ResponseEntity<Map<String, String>> generate(HttpServletRequest request) {
        SpecCaptcha captcha = new SpecCaptcha(130, 48);
        captcha.setLen(4);                 // 4 位字符
        captcha.setCharType(com.wf.captcha.base.Captcha.TYPE_ONLY_NUMBER);  // 纯数字,好认好输
        String answer = captcha.text();    // 验证码答案
        String id = UUID.randomUUID().toString();
        store.put(id, new Entry(answer, System.currentTimeMillis() + EXPIRE_MS));
        // 顺手清理过期
        cleanup();
        log.debug("captcha generated id={}", id);
        return ResponseEntity.ok(Map.of(
                "captchaId", id,
                "imageBase64", captcha.toBase64()
        ));
    }

    /**
     * 校验验证码(一次性:验证后立即失效)
     * @return true=通过
     */
    public boolean verify(String captchaId, String captchaCode) {
        if (captchaId == null || captchaCode == null) {
            return false;
        }
        Entry e = store.remove(captchaId);   // 一次性,取出即删
        if (e == null) {
            return false;
        }
        if (System.currentTimeMillis() > e.expiresAt()) {
            return false;
        }
        return e.answer().equalsIgnoreCase(captchaCode.trim());
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(en -> now > en.getValue().expiresAt());
    }
}
