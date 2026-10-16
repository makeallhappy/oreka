package net.sf.oreka.spring.repository;

import net.sf.oreka.persistent.OrkSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface SegmentRepository extends JpaRepository<OrkSegment, Integer>, JpaSpecificationExecutor<OrkSegment> {
}
