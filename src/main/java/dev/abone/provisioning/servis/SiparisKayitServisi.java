package dev.abone.provisioning.servis;

import dev.abone.provisioning.domain.Hat;
import dev.abone.provisioning.domain.HatDurumu;
import dev.abone.provisioning.domain.HatRepository;
import dev.abone.provisioning.domain.Kanal;
import dev.abone.provisioning.domain.Siparis;
import dev.abone.provisioning.domain.SiparisRepository;
import dev.abone.provisioning.domain.Tarife;
import dev.abone.provisioning.domain.TarifeRepository;
import dev.abone.provisioning.mesaj.Kuyruklar;
import dev.abone.provisioning.mesaj.SiparisMesaji;
import java.time.Clock;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Siparisi kaydeder ve kuyruga atar; ikisi tek XA isleminde. Hibernate INSERT'i commit
 * anindaki flush'a kadar erteler, bu yuzden ISTEMCI_REF tekillik ihlali mesaj gonderildikten
 * SONRA ortaya cikabilir: o durumda Atomikos iki kaynagi da geri alir ve kuyrukta sahipsiz
 * mesaj kalmaz.
 */
@Service
class SiparisKayitServisi {

    private final HatRepository hatlar;
    private final TarifeRepository tarifeler;
    private final SiparisRepository siparisler;
    private final JmsTemplate jms;
    private final Clock saat;

    SiparisKayitServisi(HatRepository hatlar, TarifeRepository tarifeler, SiparisRepository siparisler,
                        JmsTemplate jms, Clock saat) {
        this.hatlar = hatlar;
        this.tarifeler = tarifeler;
        this.siparisler = siparisler;
        this.jms = jms;
        this.saat = saat;
    }

    @Transactional
    public SiparisGorunumu kaydetVeKuyrugaAt(SiparisKomutu komut, Kanal kanal) {
        Hat hat = hatlar.findByMsisdn(komut.msisdn())
                .orElseThrow(() -> new Hatalar.KayitBulunamadi("Hat bulunamadi: " + komut.msisdn()));
        Tarife hedef = onKontrol(komut, hat);

        Siparis siparis = siparisler.save(
                new Siparis(komut.istemciRef(), komut.tip(), hat, hedef, kanal, Zaman.simdi(saat)));

        long id = siparis.getId();
        jms.convertAndSend(Kuyruklar.SIPARIS, new SiparisMesaji(id, komut.tip()), mesaj -> {
            mesaj.setLongProperty(Kuyruklar.SIPARIS_ID_OZELLIGI, id);
            return mesaj;
        });
        return SiparisGorunumu.from(siparis);
    }

    /**
     * Kullaniciya hizli geri bildirim icin senkron kontrol. Asil karar PL/SQL'de satir kilidi
     * altinda yeniden verilir; arada durum degisirse siparis REDDEDILDI olur.
     */
    private Tarife onKontrol(SiparisKomutu komut, Hat hat) {
        return switch (komut.tip()) {
            case AKTIVASYON -> {
                if (hat.getDurum() != HatDurumu.BEKLEMEDE) {
                    throw new Hatalar.GecersizIstek("Hat aktivasyona uygun degil (durum: " + hat.getDurum() + ")");
                }
                yield null;
            }
            case TARIFE_DEGISIKLIGI -> {
                if (komut.hedefTarifeKodu() == null || komut.hedefTarifeKodu().isBlank()) {
                    throw new Hatalar.GecersizIstek("Tarife degisikligi icin hedef tarife kodu zorunlu");
                }
                Tarife hedef = tarifeler.findByKod(komut.hedefTarifeKodu())
                        .orElseThrow(() -> new Hatalar.KayitBulunamadi("Tarife bulunamadi: " + komut.hedefTarifeKodu()));
                if (!hedef.isAktif()) {
                    throw new Hatalar.GecersizIstek("Tarife satista degil: " + hedef.getKod());
                }
                if (hat.getDurum() != HatDurumu.AKTIF) {
                    throw new Hatalar.GecersizIstek("Tarife degisikligi icin hat AKTIF olmali (durum: " + hat.getDurum() + ")");
                }
                if (hat.getTarife().getId().equals(hedef.getId())) {
                    throw new Hatalar.GecersizIstek("Hat zaten " + hedef.getKod() + " tarifesinde");
                }
                yield hedef;
            }
        };
    }
}
