package dev.abone.provisioning.servis;

import dev.abone.provisioning.domain.Siparis;
import dev.abone.provisioning.domain.SiparisDurumu;
import dev.abone.provisioning.domain.SiparisRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Kuyruktan gelen siparisi PL/SQL paketi uzerinden uygular. */
@Service
public class ProvisioningServisi {

    private static final Logger log = LoggerFactory.getLogger(ProvisioningServisi.class);

    private final SiparisRepository siparisler;
    private final FaturaDao fatura;
    private final Clock saat;
    private final MeterRegistry metrikler;
    private final Counter sahipsizMesaj;
    private final Counter erkenTeslim;
    private final Timer uctanUca;

    /** Islemi geri aldirip yeniden teslimi tetiklemek icin; is hatasi degildir. */
    static class SiparisHenuzGorunmuyor extends RuntimeException {
        SiparisHenuzGorunmuyor(long siparisId, int teslimSayisi) {
            super("Siparis " + siparisId + " henuz gorunmuyor (teslim " + teslimSayisi + "), yeniden denenecek");
        }
    }

    public ProvisioningServisi(SiparisRepository siparisler, FaturaDao fatura, Clock saat, MeterRegistry metrikler) {
        this.siparisler = siparisler;
        this.fatura = fatura;
        this.saat = saat;
        this.metrikler = metrikler;
        this.sahipsizMesaj = Counter.builder("provisioning.mesaj.sahipsiz")
                .description("Veritabaninda karsiligi olmayan siparis mesaji; XA dogru calisiyorsa 0 kalmali")
                .register(metrikler);
        this.erkenTeslim = Counter.builder("provisioning.mesaj.erken_teslim")
                .description("Siparis satiri commit'i gorunmeden teslim edilip geri cevrilen mesajlar")
                .register(metrikler);
        this.uctanUca = Timer.builder("provisioning.siparis.uctan_uca")
                .description("Siparisin olusturulmasindan sonuclanmasina kadar gecen sure")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(metrikler);
    }

    /**
     * 2PC'nin commit fazinda Atomikos kaynaklari sirayla commit eder; Artemis Oracle'dan once
     * commit ederse mesaj, satir gorunur olmadan teslim edilebilir. Bu yuzden bulunamayan
     * siparis hemen sahipsiz sayilmaz: istisna firlatilir, islem geri alinir ve broker mesaji
     * yeniden teslim eder. Bu kadar denemeden sonra hala yoksa gercekten sahipsizdir.
     */
    static final int SAHIPSIZ_SAYMADAN_ONCE_DENEME = 5;

    /** Dinleyicinin JTA islemine katilir; teknik hata firlatirsa mesaj yeniden teslim edilir. */
    @Transactional
    public void isle(long siparisId, int teslimSayisi) {
        Siparis siparis = siparisler.findById(siparisId).orElse(null);
        if (siparis == null) {
            if (teslimSayisi < SAHIPSIZ_SAYMADAN_ONCE_DENEME) {
                erkenTeslim.increment();
                throw new SiparisHenuzGorunmuyor(siparisId, teslimSayisi);
            }
            sahipsizMesaj.increment();
            log.warn("Siparis {} {} teslimden sonra hala yok, mesaj atlandi", siparisId, teslimSayisi);
            return;
        }
        if (siparis.getDurum() != SiparisDurumu.ALINDI) {
            // en az bir kez teslim: commit sonrasi ack kaybolursa ayni mesaj tekrar gelebilir
            log.info("Siparis {} zaten {}, tekrar teslim atlandi", siparisId, siparis.getDurum());
            return;
        }

        LocalDateTime zaman = Zaman.simdi(saat);
        long hatId = siparis.getHat().getId();
        try {
            switch (siparis.getTip()) {
                case AKTIVASYON -> {
                    FaturaDao.AktivasyonSonucu s = fatura.hatAktiveEt(hatId, zaman);
                    siparis.tamamla(s.kistUcret(), null, zaman);
                }
                case TARIFE_DEGISIKLIGI -> {
                    FaturaDao.TarifeDegisimSonucu s =
                            fatura.tarifeDegistir(hatId, siparis.getHedefTarife().getId(), zaman);
                    siparis.tamamla(s.kistUcret(), s.devredenMb(), zaman);
                }
            }
        } catch (Hatalar.IsKuraliIhlali e) {
            log.info("Siparis {} reddedildi: ORA-{} {}", siparisId, e.getOracleKodu(), e.getMessage());
            siparis.reddet(e.getMessage(), zaman);
        }

        metrikler.counter("provisioning.siparis.sonuc", "tip", siparis.getTip().name(),
                "durum", siparis.getDurum().name()).increment();
        uctanUca.record(Duration.between(siparis.getOlusturma(), zaman));
    }
}
