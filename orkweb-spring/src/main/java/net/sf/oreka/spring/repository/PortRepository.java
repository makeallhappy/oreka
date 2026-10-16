package net.sf.oreka.spring.repository;

import net.sf.oreka.persistent.OrkPort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PortRepository extends JpaRepository<OrkPort, Integer> {
}
