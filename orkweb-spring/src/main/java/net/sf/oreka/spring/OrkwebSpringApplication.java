package net.sf.oreka.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = "net.sf.oreka.persistent")
@EnableJpaRepositories(basePackages = "net.sf.oreka.spring.repository")
public class OrkwebSpringApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrkwebSpringApplication.class, args);
    }
}
