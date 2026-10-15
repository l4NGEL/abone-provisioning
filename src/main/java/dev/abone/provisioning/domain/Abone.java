package dev.abone.provisioning.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "ABONE")
public class Abone {

    @Id
    private Long id;

    @Column(nullable = false, length = 11)
    private String tckn;

    @Column(name = "AD_SOYAD", nullable = false, length = 120)
    private String adSoyad;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime olusturma;

    protected Abone() {
    }

    public Long getId() {
        return id;
    }

    public String getTckn() {
        return tckn;
    }

    public String getAdSoyad() {
        return adSoyad;
    }

    public LocalDateTime getOlusturma() {
        return olusturma;
    }
}
