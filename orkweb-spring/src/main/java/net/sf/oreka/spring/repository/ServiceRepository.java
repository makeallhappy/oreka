package net.sf.oreka.spring.repository;

import net.sf.oreka.persistent.OrkService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ServiceRepository extends JpaRepository<OrkService, Integer> {
    Optional<OrkService> findByName(String name);
}
