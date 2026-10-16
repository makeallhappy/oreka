package net.sf.oreka.spring.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import net.sf.oreka.spring.dto.PortDto;
import net.sf.oreka.spring.repository.PortRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ports")
@Tag(name = "Ports", description = "捕获端口(只读)")
public class PortController {

    private final PortRepository portRepository;

    public PortController(PortRepository portRepository) {
        this.portRepository = portRepository;
    }

    @GetMapping
    @Operation(summary = "端口列表")
    public List<PortDto> list() {
        return portRepository.findAll().stream().map(PortDto::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "端口详情")
    public ResponseEntity<PortDto> getById(@PathVariable int id) {
        return portRepository.findById(id)
                .map(PortDto::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
