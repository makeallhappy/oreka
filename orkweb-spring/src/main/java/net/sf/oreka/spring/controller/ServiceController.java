package net.sf.oreka.spring.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import net.sf.oreka.spring.dto.ServiceDto;
import net.sf.oreka.spring.repository.ServiceRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/services")
@Tag(name = "Services", description = "orkaudio 服务实例(只读)")
public class ServiceController {

    private final ServiceRepository serviceRepository;

    public ServiceController(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    @GetMapping
    @Operation(summary = "服务实例列表")
    public List<ServiceDto> list() {
        return serviceRepository.findAll().stream().map(ServiceDto::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "服务实例详情")
    public ResponseEntity<ServiceDto> getById(@PathVariable int id) {
        return serviceRepository.findById(id)
                .map(ServiceDto::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
