package dev.abone.provisioning.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "SIPARIS")
public class Siparis {

    private static final int HATA_MESAJI_UZUNLUK = 400;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "siparisSeq")
    // allocationSize sekansin INCREMENT BY degeriyle ayni: 20 kayitta bir DB'ye gidilir
    @SequenceGenerator(name = "siparisSeq", sequenceName = "SEQ_SIPARIS", allocationSize = 20)
    private Long id;

    @Column(name = "ISTEMCI_REF", nullable = false, length = 64, updatable = false)
    private String istemciRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private SiparisTipi tip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "HAT_ID", updatable = false)
    private Hat hat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "HEDEF_TARIFE_ID", updatable = false)
    private Tarife hedefTarife;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private SiparisDurumu durum;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8, updatable = false)
    private Kanal kanal;

    @Column(name = "HATA_MESAJI", length = HATA_MESAJI_UZUNLUK)
    private String hataMesaji;

    @Column(name = "KIST_UCRET", precision = 12, scale = 2)
    private BigDecimal kistUcret;

    @Column(name = "DEVREDEN_MB", precision = 12, scale = 2)
    private BigDecimal devredenMb;

    @Column(nullable = false, updatable = false)
    private LocalDateTime olusturma;

    private LocalDateTime tamamlanma;

    protected Siparis() {
    }

    public Siparis(String istemciRef, SiparisTipi tip, Hat hat, Tarife hedefTarife, Kanal kanal,
                   LocalDateTime olusturma) {
        this.istemciRef = istemciRef;
        this.tip = tip;
        this.hat = hat;
        this.hedefTarife = hedefTarife;
        this.kanal = kanal;
        this.olusturma = olusturma;
        this.durum = SiparisDurumu.ALINDI;
    }

    public void tamamla(BigDecimal kistUcret, BigDecimal devredenMb, LocalDateTime zaman) {
        durumKontrol();
        this.durum = SiparisDurumu.TAMAMLANDI;
        this.kistUcret = kistUcret;
        this.devredenMb = devredenMb;
        this.tamamlanma = zaman;
    }

    public void reddet(String sebep, LocalDateTime zaman) {
        durumKontrol();
        this.durum = SiparisDurumu.REDDEDILDI;
        this.hataMesaji = sebep.length() > HATA_MESAJI_UZUNLUK ? sebep.substring(0, HATA_MESAJI_UZUNLUK) : sebep;
        this.tamamlanma = zaman;
    }

    private void durumKontrol() {
        if (durum != SiparisDurumu.ALINDI) {
            throw new IllegalStateException("Siparis " + id + " zaten sonuclanmis: " + durum);
        }
    }

    public Long getId() {
        return id;
    }

    public String getIstemciRef() {
        return istemciRef;
    }

    public SiparisTipi getTip() {
        return tip;
    }

    public Hat getHat() {
        return hat;
    }

    public Tarife getHedefTarife() {
        return hedefTarife;
    }

    public SiparisDurumu getDurum() {
        return durum;
    }

    public Kanal getKanal() {
        return kanal;
    }

    public String getHataMesaji() {
        return hataMesaji;
    }

    public BigDecimal getKistUcret() {
        return kistUcret;
    }

    public BigDecimal getDevredenMb() {
        return devredenMb;
    }

    public LocalDateTime getOlusturma() {
        return olusturma;
    }

    public LocalDateTime getTamamlanma() {
        return tamamlanma;
    }
}
