package dev.abone.provisioning.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Durum, tarife ve kota alanlari yalnizca PKG_FATURA tarafindan degistirilir (satir kilidi
 * altinda); Java tarafi bu varligi okur. VERSIYON PL/SQL tarafindan da artirilir.
 */
@Entity
@Table(name = "HAT")
public class Hat {

    @Id
    private Long id;

    @Column(nullable = false, length = 12)
    private String msisdn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ABONE_ID")
    private Abone abone;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "TARIFE_ID")
    private Tarife tarife;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private HatDurumu durum;

    @Column(name = "AKTIVASYON_TARIHI")
    private LocalDate aktivasyonTarihi;

    @Column(name = "KOTA_MB", nullable = false, precision = 12, scale = 2)
    private BigDecimal kotaMb;

    @Column(name = "KOTA_BASLANGIC")
    private LocalDateTime kotaBaslangic;

    @Version
    private Integer versiyon;

    protected Hat() {
    }

    public Long getId() {
        return id;
    }

    public String getMsisdn() {
        return msisdn;
    }

    public Abone getAbone() {
        return abone;
    }

    public Tarife getTarife() {
        return tarife;
    }

    public HatDurumu getDurum() {
        return durum;
    }

    public LocalDate getAktivasyonTarihi() {
        return aktivasyonTarihi;
    }

    public BigDecimal getKotaMb() {
        return kotaMb;
    }

    public LocalDateTime getKotaBaslangic() {
        return kotaBaslangic;
    }
}
