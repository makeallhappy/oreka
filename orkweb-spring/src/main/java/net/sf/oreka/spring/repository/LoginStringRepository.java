package net.sf.oreka.spring.repository;

import net.sf.oreka.persistent.OrkLoginString;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LoginStringRepository extends JpaRepository<OrkLoginString, Integer> {
    Optional<OrkLoginString> findFirstByLoginStringOrderByIdAsc(String loginString);
}
