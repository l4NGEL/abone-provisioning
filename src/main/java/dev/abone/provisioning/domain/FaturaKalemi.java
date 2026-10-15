package dev.abone.provisioning.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.Immutable;

/** PKG_FATURA tarafindan yazilir; uygulama yalnizca okur. */
@Entity
@Immutable
@Table(name = "FATURA_KALEMI")
public class FaturaKalemi {

    @Id
    private Long id;

    @Column(name = "HAT_ID", nullable = false)
    private Long hatId;

    @Column(nullable = false, columnDefinition = "char(7)")
    private String donem;

    @Column(nullable = false, length = 200)
    private String aciklama;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal tutar;

    @Column(nullable = false)
    private LocalDateTime olusturma;

    protected FaturaKalemi() {
    }

    public Long getId() {
        return id;
    }

    public Long getHatId() {
        return hatId;
    }

    public String getDonem() {
        return donem;
    }

    public String getAciklama() {
        return aciklama;
    }

    public BigDecimal getTutar() {
        return tutar;
    }

    public LocalDateTime getOlusturma() {
        return olusturma;
    }
}
