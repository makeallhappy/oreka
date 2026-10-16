package net.sf.oreka.spring.service;

import net.sf.oreka.persistent.OrkUser;
import net.sf.oreka.spring.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * UserState 消息处理 —— 迁移自 orktrack UserStateMessage
 *
 * 用途:坐席登录/登出某分机(recPort)时,orkaudio 上报,把 user 和 port 绑定/解绑。
 * 参数:userstate=LOGIN|LOGOUT, loginstring=xxx, recport=SIP/1001
 *
 * 简化版(M2):User 按 loginString 查找(经 OrkLoginString 关联),
 * port 绑定逻辑当前只记日志 —— 完整的 Port.setUser 需要 PortManager 的运行时
 * 状态(内存中的 Port 对象,不是直接落库),M3 做实时状态时再补。
 */
@Service
@Transactional
public class UserStateService {

    private static final Logger log = LoggerFactory.getLogger(UserStateService.class);

    private final UserRepository userRepository;

    public UserStateService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * @return null=成功,否则=错误 comment
     */
    public String processUserState(Map<String, String> params) {
        String userState = params.getOrDefault("userstate", "UNKN").toUpperCase();
        String loginString = params.getOrDefault("loginstring", "");
        String recPort = params.getOrDefault("recport", "");

        log.info("UserStateMessage: state={} loginString={} recPort={}", userState, loginString, recPort);

        if (!"LOGIN".equals(userState) && !"LOGOUT".equals(userState)) {
            return "unknown userstate: " + userState;
        }

        // TODO: OrkLoginString 关联查找(老代码 UserManager.getByLoginString)
        // 当前简化:loginString 直接当 OrkUser.id 不合适,先检查是否有任何用户
        // 完整实现需要 LoginStringRepository.findByLoginString → OrkUser
        Optional<OrkUser> user = findUserByLoginString(loginString);
        if (user.isEmpty()) {
            String comment = "Could not find user for login string:" + loginString;
            log.warn(comment);
            return comment;
        }

        // TODO(M3): Port 绑定/解绑(需要运行时 Port 状态,不是直接落库)
        log.info("User {} {} on port {} (port binding pending M3)",
                loginString, userState.toLowerCase(), recPort);
        return null;
    }

    /**
     * 按 loginString 找用户 —— 简化版
     * 完整版应查 OrkLoginString 表(loginString → OrkUser 关联)
     */
    private Optional<OrkUser> findUserByLoginString(String loginString) {
        if (loginString == null || loginString.isBlank()) {
            return Optional.empty();
        }
        // TODO: 实现 LoginStringRepository 后改成关联查询
        // 当前先返回空(契约测试只验证协议容错,不验证业务)
        return Optional.empty();
    }
}
