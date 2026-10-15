package dev.abone.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.abone.provisioning.domain.Kanal;
import dev.abone.provisioning.domain.SiparisTipi;
import dev.abone.provisioning.mesaj.Kuyruklar;
import dev.abone.provisioning.mesaj.SiparisMesaji;
import dev.abone.provisioning.servis.SiparisKomutu;
import dev.abone.provisioning.servis.SiparisServisi;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsTemplate;

/**
 * Siparis satiri ile kuyruk mesajinin atomikligi. Hibernate INSERT'i commit'e erteledigi icin
 * es zamanli ayni istemciRef'li isteklerde mesaj gonderildikten SONRA tekillik ihlali olusur.
 * Her istek sekanstan kendi siparis ID'sini alip mesaja yazar; XA olmasaydi kaybeden isteklerin
 * mesajlari DB'de karsiligi olmayan ID'lerle kuyruga duser ve dinleyici bunlari "sahipsiz"
 * sayardi. (Negatif kontrol: JMS XA'siz fabrikaya cevrildiginde bu test kirmizi oluyor.)
 */
class XaIslemTesti extends EntegrasyonTesti {

    private static final int ES_ZAMANLI_ISTEK = 8;

    @Autowired
    SiparisServisi siparisler;

    @Autowired
    MeterRegistry metrikler;

    @Autowired
    JmsTemplate jms;

    @Test
    void esZamanliAyniIstemciRefTekSatirTekIslemVeSahipsizMesajYok() throws Exception {
        String msisdn = yeniHat("AKTIF", GNC10, 10240, SIMDI.minusDays(3));
        String ref = "xa-yaris-" + System.nanoTime();
        double oncekiCakisma = sayac("provisioning.siparis.istemci_ref_cakismasi");
        double oncekiSahipsiz = sayac("provisioning.mesaj.sahipsiz");

        List<SiparisServisi.Sonuc> sonuclar = esZamanliCalistir(() -> siparisler.olustur(
                new SiparisKomutu(ref, SiparisTipi.TARIFE_DEGISIKLIGI, msisdn, "PLT20"), Kanal.REST));

        long siparisId = sonuclar.get(0).siparis().id();
        assertThat(sonuclar).allSatisfy(s -> assertThat(s.siparis().id()).isEqualTo(siparisId));
        assertThat(sonuclar).filteredOn(s -> !s.tekrarIstek()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SIPARIS WHERE ISTEMCI_REF = ?", Integer.class, ref))
                .isEqualTo(1);
        // Yarisin gercekten olustugunu, yani en az bir istegin on kontrolu gecip mesaj
        // gonderdikten sonra commit'te geri alindigini dogrula; yoksa test XA'yi sinamamis olur
        assertThat(sayac("provisioning.siparis.istemci_ref_cakismasi") - oncekiCakisma).isGreaterThan(0);

        await().atMost(Duration.ofSeconds(20)).until(
                () -> jdbc.queryForObject("SELECT DURUM FROM SIPARIS WHERE ID = ?", String.class, siparisId),
                "TAMAMLANDI"::equals);
        // Kuyruga sizmis bir mesaj olsaydi bu surede tuketilip sahipsiz sayilirdi
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(4))
                .until(() -> sayac("provisioning.mesaj.sahipsiz") == oncekiSahipsiz);
        // Tarife degisikligi tam bir kez uygulandi: bir iade + bir kist kalemi
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM FATURA_KALEMI WHERE HAT_ID = ?", Integer.class, hatId(msisdn)))
                .isEqualTo(2);
    }

    @Test
    void karsiligiOlmayanMesajBirkacTeslimdenSonraSahipsizSayilir() {
        double oncekiSahipsiz = sayac("provisioning.mesaj.sahipsiz");
        double oncekiErken = sayac("provisioning.mesaj.erken_teslim");

        // Atomikos JMS oturumu JTA islemi ister
        islem.executeWithoutResult(t -> jms.convertAndSend(Kuyruklar.SIPARIS,
                new SiparisMesaji(987_654_321L, SiparisTipi.AKTIVASYON)));

        await().atMost(Duration.ofSeconds(20))
                .until(() -> sayac("provisioning.mesaj.sahipsiz") - oncekiSahipsiz == 1);
        // JMSXDeliveryCount 1..4 geri cevrildi, 5. teslimde sahipsiz sayildi
        assertThat(sayac("provisioning.mesaj.erken_teslim") - oncekiErken).isEqualTo(4);
    }

    private <T> List<T> esZamanliCalistir(Callable<T> is) throws Exception {
        CyclicBarrier baslangic = new CyclicBarrier(ES_ZAMANLI_ISTEK);
        ExecutorService havuz = Executors.newFixedThreadPool(ES_ZAMANLI_ISTEK);
        try {
            List<Future<T>> gelecekler = new ArrayList<>();
            for (int i = 0; i < ES_ZAMANLI_ISTEK; i++) {
                gelecekler.add(havuz.submit(() -> {
                    baslangic.await();
                    return is.call();
                }));
            }
            List<T> sonuc = new ArrayList<>();
            for (Future<T> f : gelecekler) {
                sonuc.add(f.get());
            }
            return sonuc;
        } finally {
            havuz.shutdownNow();
        }
    }

    private double sayac(String ad) {
        return metrikler.counter(ad).count();
    }
}
