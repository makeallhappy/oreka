package net.sf.oreka.spring.service;

import net.sf.oreka.persistent.OrkLoginString;
import net.sf.oreka.persistent.OrkUser;
import net.sf.oreka.spring.dto.UserCreateRequest;
import net.sf.oreka.spring.dto.UserDto;
import net.sf.oreka.spring.dto.UserUpdateRequest;
import net.sf.oreka.spring.repository.LoginStringRepository;
import net.sf.oreka.spring.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * 用户管理 —— 含 BCrypt 密码迁移
 *
 * 老系统:密码明文存储(HibernateManager 硬编码 admin/admin)
 * 新系统:BCrypt 哈希。登录验证时自动识别:
 *   - 以 $2a$/$2b$ 开头 → BCrypt 哈希,用 matches 验证
 *   - 否则 → 老明文,直接比对;验证成功后顺手升级为 BCrypt(透明迁移)
 */
@Service
@Transactional
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final LoginStringRepository loginStringRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(UserRepository userRepository, LoginStringRepository loginStringRepository) {
        this.userRepository = userRepository;
        this.loginStringRepository = loginStringRepository;
    }

    // ==================== 查询 ====================

    @Transactional(readOnly = true)
    public List<UserDto> findAll() {
        return userRepository.findAll().stream()
                .filter(u -> !u.isDeleted())
                .map(UserDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<UserDto> findById(int id) {
        return userRepository.findById(id)
                .filter(u -> !u.isDeleted())
                .map(UserDto::from);
    }

    @Transactional(readOnly = true)
    public Optional<OrkUser> findByLoginString(String loginString) {
        return loginStringRepository.findByLoginString(loginString)
                .map(OrkLoginString::getUser)
                .filter(u -> !u.isDeleted() && !u.isDisabled());
    }

    // ==================== 创建 ====================

    public UserDto create(UserCreateRequest req) {
        OrkUser user = new OrkUser();
        user.setFirstname(req.firstname());
        user.setLastname(req.lastname());
        user.setEmail(req.email() == null ? "" : req.email());
        user.setPassword(passwordEncoder.encode(req.password()));
        user.setDateCreated(new Date());
        user = userRepository.save(user);

        // 创建 loginString(默认=firstname)
        String loginString = (req.loginString() != null && !req.loginString().isBlank())
                ? req.loginString() : req.firstname();
        if (loginString != null && !loginString.isBlank()) {
            OrkLoginString ls = new OrkLoginString();
            ls.setLoginString(loginString);
            ls.setUser(user);
            loginStringRepository.save(ls);
        }

        log.info("Created user id={} loginString={}", user.getId(), loginString);
        return UserDto.from(user);
    }

    // ==================== 更新 ====================

    public Optional<UserDto> update(int id, UserUpdateRequest req) {
        return userRepository.findById(id).map(user -> {
            if (req.firstname() != null) user.setFirstname(req.firstname());
            if (req.lastname() != null) user.setLastname(req.lastname());
            if (req.email() != null) user.setEmail(req.email());
            if (req.password() != null && !req.password().isBlank()) {
                user.setPassword(passwordEncoder.encode(req.password()));
            }
            if (req.disabled() != null) {
                user.setDisabled(req.disabled());
                if (req.disabled()) user.setDateDisabled(new Date());
            }
            return UserDto.from(userRepository.save(user));
        });
    }

    // ==================== 删除(软删) ====================

    public boolean delete(int id) {
        return userRepository.findById(id).map(user -> {
            user.setDeleted(true);
            user.setDateDeleted(new Date());
            userRepository.save(user);
            log.info("Soft-deleted user id={}", id);
            return true;
        }).orElse(false);
    }

    // ==================== 登录验证(BCrypt 透明迁移) ====================

    /**
     * 验证 loginString + password
     * 老明文密码验证成功后自动升级为 BCrypt
     */
    public Optional<OrkUser> authenticate(String loginString, String rawPassword) {
        Optional<OrkUser> userOpt = findByLoginString(loginString);
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }
        OrkUser user = userOpt.get();
        String stored = user.getPassword();

        if (isBcryptHash(stored)) {
            // BCrypt 哈希
            if (passwordEncoder.matches(rawPassword, stored)) {
                return Optional.of(user);
            }
        } else {
            // 老明文:直接比对,成功后升级
            if (stored.equals(rawPassword)) {
                user.setPassword(passwordEncoder.encode(rawPassword));
                userRepository.save(user);
                log.info("Upgraded plaintext password to BCrypt for user id={}", user.getId());
                return Optional.of(user);
            }
        }
        return Optional.empty();
    }

    private boolean isBcryptHash(String s) {
        return s != null && (s.startsWith("$2a$") || s.startsWith("$2b$") || s.startsWith("$2y$"));
    }
}
