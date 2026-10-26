package dev.abone.provisioning.servis;

import dev.abone.provisioning.domain.Kanal;
import dev.abone.provisioning.domain.SiparisRepository;
import dev.abone.provisioning.domain.SiparisTipi;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * REST, SOAP ve JSF'in ortak giris noktasi. Bilerek islemsiz: kayit islemi commit'te
 * basarisiz olursa (es zamanli ayni istemciRef) kazanan siparisi yeni bir islemde okuyabilmek
 * icin.
 */
@Service
public class SiparisServisi {

    private static final Logger log = LoggerFactory.getLogger(SiparisServisi.class);

    public record Sonuc(SiparisGorunumu siparis, boolean tekrarIstek) {
    }

    private final SiparisKayitServisi kayit;
    private final SiparisRepository siparisler;
    private final Counter cakismaSayaci;
    // olustur() islemsiz oldugundan icerideki okumalar proxy'den gecmez; salt-okunur islemi acikca acar
    private final TransactionTemplate saltOkunur;

    public SiparisServisi(SiparisKayitServisi kayit, SiparisRepository siparisler, MeterRegistry metrikler,
            PlatformTransactionManager islemYoneticisi) {
        this.kayit = kayit;
        this.siparisler = siparisler;
        this.saltOkunur = new TransactionTemplate(islemYoneticisi);
        this.saltOkunur.setReadOnly(true);
        this.cakismaSayaci = Counter.builder("provisioning.siparis.istemci_ref_cakismasi")
                .description("Commit aninda tekillik ihlaliyle geri alinan (XA rollback) es zamanli tekrar istekler")
                .register(metrikler);
    }

    public Sonuc olustur(SiparisKomutu komut, Kanal kanal) {
        Optional<SiparisGorunumu> mevcut = saltOkunurIstemciRefIle(komut.istemciRef());
        if (mevcut.isPresent()) {
            return new Sonuc(ayniIstekMi(mevcut.get(), komut), true);
        }
        try {
            return new Sonuc(kayit.kaydetVeKuyrugaAt(komut, kanal), false);
        } catch (Hatalar.KayitBulunamadi | Hatalar.GecersizIstek e) {
            throw e;
        } catch (RuntimeException e) {
            Optional<SiparisGorunumu> kazanan = saltOkunurIstemciRefIle(komut.istemciRef());
            if (kazanan.isEmpty()) {
                throw e;
            }
            cakismaSayaci.increment();
            log.info("istemciRef={} icin es zamanli tekrar istek geri alindi, mevcut siparis {} donuluyor",
                    komut.istemciRef(), kazanan.get().id());
            return new Sonuc(ayniIstekMi(kazanan.get(), komut), true);
        }
    }

    private Optional<SiparisGorunumu> saltOkunurIstemciRefIle(String istemciRef) {
        return Optional.ofNullable(saltOkunur.execute(
                durum -> siparisler.findByIstemciRef(istemciRef).map(SiparisGorunumu::from).orElse(null)));
    }

    private static SiparisGorunumu ayniIstekMi(SiparisGorunumu mevcut, SiparisKomutu komut) {
        boolean ayni = mevcut.tip() == komut.tip()
                && mevcut.msisdn().equals(komut.msisdn())
                && (komut.tip() == SiparisTipi.AKTIVASYON
                    || Objects.equals(mevcut.hedefTarifeKodu(), bosIseNull(komut.hedefTarifeKodu())));
        if (!ayni) {
            throw new Hatalar.IstemciRefCakismasi(
                    "istemciRef '" + komut.istemciRef() + "' farkli bir istek icin kullanilmis (siparis " + mevcut.id() + ")");
        }
        return mevcut;
    }

    private static String bosIseNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    @Transactional(readOnly = true)
    public Optional<SiparisGorunumu> istemciRefIle(String istemciRef) {
        return siparisler.findByIstemciRef(istemciRef).map(SiparisGorunumu::from);
    }

    @Transactional(readOnly = true)
    public SiparisGorunumu bul(long id) {
        return siparisler.findWithHatById(id).map(SiparisGorunumu::from)
                .orElseThrow(() -> new Hatalar.KayitBulunamadi("Siparis bulunamadi: " + id));
    }

    @Transactional(readOnly = true)
    public List<SiparisGorunumu> sonSiparisler(int adet) {
        return siparisler.sonSiparisler(PageRequest.of(0, adet)).stream().map(SiparisGorunumu::from).toList();
    }
}
