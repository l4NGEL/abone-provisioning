package dev.abone.provisioning.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SiparisRepository extends JpaRepository<Siparis, Long> {

    @EntityGraph(attributePaths = {"hat", "hedefTarife"})
    Optional<Siparis> findWithHatById(Long id);

    @EntityGraph(attributePaths = {"hat", "hedefTarife"})
    Optional<Siparis> findByIstemciRef(String istemciRef);

    @EntityGraph(attributePaths = {"hat", "hedefTarife"})
    @Query("select s from Siparis s order by s.olusturma desc, s.id desc")
    List<Siparis> sonSiparisler(Pageable sayfa);

    long countByDurum(SiparisDurumu durum);
}
