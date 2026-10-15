package dev.abone.provisioning.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.Immutable;

/** Tarife katalogu bu servisten degistirilmez; urun yonetimi sisteminden gelir. */
@Entity
@Immutable
@Table(name = "TARIFE")
public class Tarife {

    @Id
    private Integer id;

    @Column(nullable = false, length = 20)
    private String kod;

    @Column(nullable = false, length = 100)
    private String ad;

    @Column(name = "AYLIK_UCRET", nullable = false, precision = 10, scale = 2)
    private BigDecimal aylikUcret;

    @Column(name = "DATA_MB", nullable = false)
    private Integer dataMb;

    @Column(nullable = false)
    private Integer dakika;

    @Convert(converter = EvetHayirCevirici.class)
    @Column(nullable = false, columnDefinition = "char(1)")
    private Boolean aktif;

    protected Tarife() {
    }

    public Integer getId() {
        return id;
    }

    public String getKod() {
        return kod;
    }

    public String getAd() {
        return ad;
    }

    public BigDecimal getAylikUcret() {
        return aylikUcret;
    }

    public Integer getDataMb() {
        return dataMb;
    }

    public Integer getDakika() {
        return dakika;
    }

    public boolean isAktif() {
        return Boolean.TRUE.equals(aktif);
    }
}
