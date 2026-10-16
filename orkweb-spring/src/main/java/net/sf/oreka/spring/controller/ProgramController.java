package net.sf.oreka.spring.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import net.sf.oreka.spring.dto.ProgramDto;
import net.sf.oreka.spring.dto.ProgramRequest;
import net.sf.oreka.spring.service.ProgramService;
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
@RequestMapping("/api/v1/programs")
@Tag(name = "Programs", description = "录音计划管理")
public class ProgramController {

    private final ProgramService programService;

    public ProgramController(ProgramService programService) {
        this.programService = programService;
    }

    @GetMapping
    @Operation(summary = "计划列表(不含已废弃)")
    public List<ProgramDto> list() {
        return programService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "计划详情")
    public ResponseEntity<ProgramDto> getById(@PathVariable int id) {
        return programService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "创建计划")
    public ResponseEntity<ProgramDto> create(@Valid @RequestBody ProgramRequest req) {
        // TODO(JWT 落地后):从 SecurityContext 取当前用户 id 作为 creator
        ProgramDto created = programService.create(req, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新计划")
    public ResponseEntity<ProgramDto> update(@PathVariable int id, @Valid @RequestBody ProgramRequest req) {
        return programService.update(id, req)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "废弃计划(软删)")
    public ResponseEntity<Void> delete(@PathVariable int id) {
        return programService.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
