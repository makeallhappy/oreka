package net.sf.oreka.spring.repository;

import net.sf.oreka.persistent.OrkProgram;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProgramRepository extends JpaRepository<OrkProgram, Integer> {
    List<OrkProgram> findByDiscardedFalse();
}
