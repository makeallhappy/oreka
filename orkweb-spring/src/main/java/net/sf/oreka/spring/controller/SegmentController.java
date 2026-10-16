package net.sf.oreka.spring.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import net.sf.oreka.spring.dto.SegmentDto;
import net.sf.oreka.spring.dto.SegmentSearchCriteria;
import net.sf.oreka.spring.service.SegmentService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/segments")
@Tag(name = "Segments", description = "录音段查询")
public class SegmentController {

    private final SegmentService segmentService;

    public SegmentController(SegmentService segmentService) {
        this.segmentService = segmentService;
    }

    @GetMapping
    @Operation(summary = "分页搜索录音段", description = "按主被叫/方向/端口/时长/时间范围过滤")
    public Page<SegmentDto> search(
            @ParameterObject SegmentSearchCriteria criteria,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC)
            @ParameterObject Pageable pageable) {
        return segmentService.search(criteria, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "录音段详情")
    public ResponseEntity<SegmentDto> getById(@PathVariable int id) {
        return segmentService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
