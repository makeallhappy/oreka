package net.sf.oreka.spring.service;

import jakarta.persistence.criteria.Predicate;
import net.sf.oreka.persistent.OrkSegment;
import net.sf.oreka.spring.dto.SegmentSearchCriteria;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * 录音段动态查询条件(JPA Specification)
 */
public class SegmentSpecifications {

    public static Specification<OrkSegment> fromCriteria(SegmentSearchCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (c.localParty() != null && !c.localParty().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("localParty")), "%" + c.localParty().toLowerCase() + "%"));
            }
            if (c.remoteParty() != null && !c.remoteParty().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("remoteParty")), "%" + c.remoteParty().toLowerCase() + "%"));
            }
            if (c.portName() != null && !c.portName().isBlank()) {
                predicates.add(cb.equal(root.get("portName"), c.portName()));
            }
            if (c.direction() != null) {
                predicates.add(cb.equal(root.get("direction"), c.direction()));
            }
            if (c.minDuration() != null) {
                predicates.add(cb.ge(root.get("duration"), c.minDuration()));
            }
            if (c.maxDuration() != null) {
                predicates.add(cb.le(root.get("duration"), c.maxDuration()));
            }
            if (c.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), c.from()));
            }
            if (c.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), c.to()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
