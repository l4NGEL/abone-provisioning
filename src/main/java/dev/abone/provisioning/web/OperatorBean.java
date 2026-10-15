package dev.abone.provisioning.web;

import dev.abone.provisioning.domain.HatDurumu;
import dev.abone.provisioning.domain.Kanal;
import dev.abone.provisioning.domain.SiparisTipi;
import dev.abone.provisioning.servis.Hatalar;
import dev.abone.provisioning.servis.HatServisi;
import dev.abone.provisioning.servis.SiparisGorunumu;
import dev.abone.provisioning.servis.SiparisKomutu;
import dev.abone.provisioning.servis.SiparisServisi;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Cagri merkezi operatoru ekrani. Her form gosteriminde bir istemciRef uretilir; cift
 * tiklama veya ag kesintisi sonrasi yeniden gonderim ayni siparise duser.
 */
@Component
@ViewScoped
public class OperatorBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final int LISTE_BOYU = 15;

    private final transient SiparisServisi siparisler;
    private final transient HatServisi hatlar;

    private String msisdn;
    private HatServisi.HatOzeti hat;
    private List<HatServisi.FaturaKalemiOzeti> faturaKalemleri = List.of();
    private List<HatServisi.TarifeOzeti> tarifeler = List.of();
    private List<SiparisGorunumu> sonSiparisler = List.of();
    private String hedefTarifeKodu;
    private String istemciRef = yeniRef();

    public OperatorBean(SiparisServisi siparisler, HatServisi hatlar) {
        this.siparisler = siparisler;
        this.hatlar = hatlar;
    }

    @PostConstruct
    void yukle() {
        tarifeler = hatlar.satistakiTarifeler();
        yenile();
    }

    public void sorgula() {
        try {
            hat = hatlar.ozet(msisdn);
            faturaKalemleri = hatlar.faturaKalemleri(msisdn);
            hedefTarifeKodu = null;
        } catch (Hatalar.KayitBulunamadi e) {
            hat = null;
            faturaKalemleri = List.of();
            mesaj(FacesMessage.SEVERITY_WARN, e.getMessage(), "msisdn");
        }
    }

    public void aktiveEt() {
        siparisVer(SiparisTipi.AKTIVASYON, null);
    }

    public void tarifeDegistir() {
        siparisVer(SiparisTipi.TARIFE_DEGISIKLIGI, hedefTarifeKodu);
    }

    private void siparisVer(SiparisTipi tip, String hedef) {
        if (hat == null) {
            return;
        }
        try {
            SiparisServisi.Sonuc sonuc = siparisler.olustur(
                    new SiparisKomutu(istemciRef, tip, hat.msisdn(), hedef), Kanal.JSF);
            String metin = sonuc.tekrarIstek()
                    ? "Bu istek zaten alinmisti (siparis #" + sonuc.siparis().id() + ")"
                    : "Siparis #" + sonuc.siparis().id() + " alindi, isleniyor";
            mesaj(FacesMessage.SEVERITY_INFO, metin, null);
            istemciRef = yeniRef();
            yenile();
        } catch (Hatalar.GecersizIstek | Hatalar.KayitBulunamadi | Hatalar.IstemciRefCakismasi e) {
            mesaj(FacesMessage.SEVERITY_ERROR, e.getMessage(), null);
        }
    }

    /** p:poll tarafindan cagrilir; bekleyen siparis kalmadiysa hat bilgisi de tazelenir. */
    public void yenile() {
        boolean onceBekleyenVardi = isBekleyenVar();
        sonSiparisler = siparisler.sonSiparisler(LISTE_BOYU);
        if (hat != null && onceBekleyenVardi && !isBekleyenVar()) {
            sorgula();
        }
    }

    public boolean isBekleyenVar() {
        return sonSiparisler.stream().anyMatch(s -> !s.sonuclandi());
    }

    public boolean isAktivasyonaUygun() {
        return hat != null && hat.durum() == HatDurumu.BEKLEMEDE;
    }

    public boolean isTarifeDegisimineUygun() {
        return hat != null && hat.durum() == HatDurumu.AKTIF;
    }

    /** Kota cubugu icin 0-100 arasi kullanilabilir oran. */
    public int getKalanYuzde() {
        if (hat == null || hat.kotaMb() == null || hat.kotaMb().signum() == 0) {
            return 0;
        }
        return hat.kalanKotaMb().multiply(BigDecimal.valueOf(100))
                .divide(hat.kotaMb(), 0, RoundingMode.HALF_UP).intValue();
    }

    public List<HatServisi.TarifeOzeti> getHedefTarifeler() {
        return tarifeler.stream().filter(t -> hat == null || !t.kod().equals(hat.tarifeKodu())).toList();
    }

    private static void mesaj(FacesMessage.Severity seviye, String metin, String alan) {
        FacesContext.getCurrentInstance().addMessage(alan, new FacesMessage(seviye, metin, null));
    }

    private static String yeniRef() {
        return "jsf-" + UUID.randomUUID();
    }

    public String getMsisdn() {
        return msisdn;
    }

    public void setMsisdn(String msisdn) {
        this.msisdn = msisdn;
    }

    public HatServisi.HatOzeti getHat() {
        return hat;
    }

    public List<HatServisi.FaturaKalemiOzeti> getFaturaKalemleri() {
        return faturaKalemleri;
    }

    public List<SiparisGorunumu> getSonSiparisler() {
        return sonSiparisler;
    }

    public String getHedefTarifeKodu() {
        return hedefTarifeKodu;
    }

    public void setHedefTarifeKodu(String hedefTarifeKodu) {
        this.hedefTarifeKodu = hedefTarifeKodu;
    }
}
