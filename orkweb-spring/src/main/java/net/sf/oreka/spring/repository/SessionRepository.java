package net.sf.oreka.spring.repository;

import net.sf.oreka.persistent.OrkSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SessionRepository extends JpaRepository<OrkSession, Integer> {
}
