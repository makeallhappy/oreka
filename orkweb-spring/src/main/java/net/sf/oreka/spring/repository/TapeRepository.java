package net.sf.oreka.spring.repository;

import net.sf.oreka.persistent.OrkTape;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TapeRepository extends JpaRepository<OrkTape, Integer>, JpaSpecificationExecutor<OrkTape> {
    Optional<OrkTape> findFirstByNativeCallIdOrderByIdDesc(String nativeCallId);
}
