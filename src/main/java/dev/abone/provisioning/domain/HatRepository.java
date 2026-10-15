package dev.abone.provisioning.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HatRepository extends JpaRepository<Hat, Long> {

    @EntityGraph(attributePaths = {"abone", "tarife"})
    Optional<Hat> findByMsisdn(String msisdn);
}
