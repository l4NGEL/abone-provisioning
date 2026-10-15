package dev.abone.provisioning.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaturaKalemiRepository extends JpaRepository<FaturaKalemi, Long> {

    List<FaturaKalemi> findByHatIdOrderById(Long hatId);
}
