package net.sf.oreka.spring.repository;

import net.sf.oreka.persistent.OrkUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<OrkUser, Integer> {
}
