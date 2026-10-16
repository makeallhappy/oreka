package net.sf.oreka.spring.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import net.sf.oreka.spring.dto.TapeDto;
import net.sf.oreka.spring.repository.TapeRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tapes")
@Tag(name = "Tapes", description = "录音(tape)查询")
public class TapeController {

    private final TapeRepository tapeRepository;

    public TapeController(TapeRepository tapeRepository) {
        this.tapeRepository = tapeRepository;
    }

    @GetMapping("/{id}")
    @Operation(summary = "tape 详情")
    public ResponseEntity<TapeDto> getById(@PathVariable int id) {
        return tapeRepository.findById(id)
                .map(TapeDto::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
