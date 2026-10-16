package net.sf.oreka.spring.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import net.sf.oreka.spring.dto.UserCreateRequest;
import net.sf.oreka.spring.dto.UserDto;
import net.sf.oreka.spring.dto.UserUpdateRequest;
import net.sf.oreka.spring.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "用户管理")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "用户列表(不含已删除)")
    public List<UserDto> list() {
        return userService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "用户详情")
    public ResponseEntity<UserDto> getById(@PathVariable int id) {
        return userService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "创建用户(密码 BCrypt 哈希,自动建 loginString)")
    public ResponseEntity<UserDto> create(@Valid @RequestBody UserCreateRequest req) {
        UserDto created = userService.create(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新用户(密码留空则不改)")
    public ResponseEntity<UserDto> update(@PathVariable int id, @Valid @RequestBody UserUpdateRequest req) {
        return userService.update(id, req)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "软删除用户")
    public ResponseEntity<Void> delete(@PathVariable int id) {
        return userService.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
