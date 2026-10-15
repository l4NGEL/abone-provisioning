package dev.abone.provisioning.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TarifeRepository extends JpaRepository<Tarife, Integer> {

    Optional<Tarife> findByKod(String kod);

    List<Tarife> findByAktifOrderByAylikUcret(Boolean aktif);
}
