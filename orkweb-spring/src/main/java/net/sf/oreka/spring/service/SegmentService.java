package net.sf.oreka.spring.service;

import net.sf.oreka.spring.dto.SegmentDto;
import net.sf.oreka.spring.dto.SegmentSearchCriteria;
import net.sf.oreka.spring.repository.SegmentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class SegmentService {

    private final SegmentRepository segmentRepository;

    public SegmentService(SegmentRepository segmentRepository) {
        this.segmentRepository = segmentRepository;
    }

    public Page<SegmentDto> search(SegmentSearchCriteria criteria, Pageable pageable) {
        return segmentRepository
                .findAll(SegmentSpecifications.fromCriteria(criteria), pageable)
                .map(SegmentDto::from);
    }

    public Optional<SegmentDto> findById(int id) {
        return segmentRepository.findById(id).map(SegmentDto::from);
    }
}
