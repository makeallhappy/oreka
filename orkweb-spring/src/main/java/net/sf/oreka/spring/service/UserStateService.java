package net.sf.oreka.spring.service;

import net.sf.oreka.persistent.OrkLoginString;
import net.sf.oreka.persistent.OrkUser;
import net.sf.oreka.spring.repository.LoginStringRepository;
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
    private final LoginStringRepository loginStringRepository;
    private final PortUserRegistry portUserRegistry;

    public UserStateService(UserRepository userRepository, LoginStringRepository loginStringRepository,
                            PortUserRegistry portUserRegistry) {
        this.userRepository = userRepository;
        this.loginStringRepository = loginStringRepository;
        this.portUserRegistry = portUserRegistry;
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

        // loginString → OrkUser 关联查询(OrkLoginString 表)
        Optional<OrkUser> user = findUserByLoginString(loginString);
        if (user.isEmpty()) {
            String comment = "Could not find user for login string:" + loginString;
            log.warn(comment);
            return comment;
        }

        // port 绑定/解绑(运行时内存状态,对齐老 PortManager 设计)
        OrkUser u = user.get();
        if ("LOGIN".equals(userState)) {
            portUserRegistry.bind(recPort, u.getId(), loginString);
        } else {
            portUserRegistry.unbind(recPort);
        }
        log.info("User {} (id={}) {} on port {}", loginString, u.getId(), userState.toLowerCase(), recPort);
        return null;
    }

    /**
     * 按 loginString 找用户 —— 经 OrkLoginString 表关联(对齐老代码 UserManager.getByLoginString)
     */
    private Optional<OrkUser> findUserByLoginString(String loginString) {
        if (loginString == null || loginString.isBlank()) {
            return Optional.empty();
        }
        return loginStringRepository.findFirstByLoginStringOrderByIdAsc(loginString)
                .map(OrkLoginString::getUser);
    }
}
