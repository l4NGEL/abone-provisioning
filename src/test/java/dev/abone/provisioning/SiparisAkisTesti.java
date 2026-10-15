package dev.abone.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import dev.abone.provisioning.mesaj.SiparisDinleyici;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jms.config.JmsListenerEndpointRegistry;

/** REST -> Oracle + JMS (XA) -> dinleyici -> PL/SQL -> sonuc; gercek altyapi uzerinde. */
class SiparisAkisTesti extends EntegrasyonTesti {

    @Autowired
    TestRestTemplate http;

    @Autowired
    JmsListenerEndpointRegistry dinleyiciler;

    @Test
    void aktivasyonSiparisiAsenkronTamamlanir() {
        String msisdn = yeniHat("BEKLEMEDE", GNC10, 0, null);

        ResponseEntity<JsonNode> yanit = siparisVer("akis-akt-1", "AKTIVASYON", msisdn, null);

        assertThat(yanit.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(yanit.getHeaders().getLocation()).hasPath("/api/siparisler/" + yanit.getBody().get("id").asLong());
        assertThat(yanit.getBody().get("durum").asText()).isEqualTo("ALINDI");

        JsonNode sonuc = sonuclanmasiniBekle(yanit.getBody().get("id").asLong());
        assertThat(sonuc.get("durum").asText()).isEqualTo("TAMAMLANDI");
        assertThat(sonuc.get("kistUcret").decimalValue()).isEqualByComparingTo("169.35");

        JsonNode hat = http.getForObject("/api/hatlar/" + msisdn, JsonNode.class);
        assertThat(hat.get("durum").asText()).isEqualTo("AKTIF");
        assertThat(hat.get("kalanKotaMb").decimalValue()).isEqualByComparingTo("6936.77");
    }

    @Test
    void ayniIstemciRefIleTekrarIstekAyniSiparisiDoner() {
        String msisdn = yeniHat("BEKLEMEDE", EKO5, 0, null);

        ResponseEntity<JsonNode> ilk = siparisVer("akis-tekrar-1", "AKTIVASYON", msisdn, null);
        ResponseEntity<JsonNode> tekrar = siparisVer("akis-tekrar-1", "AKTIVASYON", msisdn, null);

        assertThat(tekrar.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tekrar.getBody().get("id").asLong()).isEqualTo(ilk.getBody().get("id").asLong());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SIPARIS WHERE ISTEMCI_REF = 'akis-tekrar-1'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void ayniIstemciRefFarkliIstekIcinKullanilirsa409() {
        String msisdn = yeniHat("BEKLEMEDE", EKO5, 0, null);
        String baska = yeniHat("BEKLEMEDE", EKO5, 0, null);
        siparisVer("akis-cakisma-1", "AKTIVASYON", msisdn, null);

        ResponseEntity<JsonNode> yanit = siparisVer("akis-cakisma-1", "AKTIVASYON", baska, null);

        assertThat(yanit.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(yanit.getBody().get("detail").asText()).contains("farkli bir istek");
    }

    @Test
    void onKontroldenGecipPlsqlDeReddedilenSiparisReddedildiOlur() {
        // Ayni beklemedeki hat icin iki aktivasyon: ikisi de on kontrolden gecer (hat hala
        // BEKLEMEDE), dinleyici durdurulmus oldugu icin ikisi de kuyrukta bekler. Biri
        // uygulandiktan sonra digeri PL/SQL'deki satir kilidi altindaki kontrolde reddedilir.
        String msisdn = yeniHat("BEKLEMEDE", GNC10, 0, null);
        dinleyiciler.getListenerContainer(SiparisDinleyici.ID).stop();
        long a;
        long b;
        try {
            a = siparisVer("akis-yaris-a", "AKTIVASYON", msisdn, null).getBody().get("id").asLong();
            b = siparisVer("akis-yaris-b", "AKTIVASYON", msisdn, null).getBody().get("id").asLong();
        } finally {
            dinleyiciler.getListenerContainer(SiparisDinleyici.ID).start();
        }

        JsonNode sa = sonuclanmasiniBekle(a);
        JsonNode sb = sonuclanmasiniBekle(b);

        assertThat(java.util.List.of(sa.get("durum").asText(), sb.get("durum").asText()))
                .containsExactlyInAnyOrder("TAMAMLANDI", "REDDEDILDI");
        JsonNode reddedilen = "REDDEDILDI".equals(sa.get("durum").asText()) ? sa : sb;
        assertThat(reddedilen.get("hataMesaji").asText()).contains("Sadece BEKLEMEDE");
        // tek fatura kalemi: reddedilen siparis yan etki birakmadi
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM FATURA_KALEMI WHERE HAT_ID = ?", Integer.class, hatId(msisdn)))
                .isEqualTo(1);
    }

    @Test
    void senkronOnKontrol422Doner() {
        String msisdn = yeniHat("AKTIF", GNC10, 10240, SIMDI.minusDays(3));

        ResponseEntity<JsonNode> ayniTarife = siparisVer("akis-422-a", "TARIFE_DEGISIKLIGI", msisdn, "GNC10");
        ResponseEntity<JsonNode> aktifeAktivasyon = siparisVer("akis-422-b", "AKTIVASYON", msisdn, null);
        ResponseEntity<JsonNode> pasifTarife = siparisVer("akis-422-c", "TARIFE_DEGISIKLIGI", msisdn, "ESKI4");

        assertThat(ayniTarife.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(aktifeAktivasyon.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(pasifTarife.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SIPARIS WHERE ISTEMCI_REF LIKE 'akis-422-%'", Integer.class))
                .isZero();
    }

    @Test
    void dogrulamaVeBulunamadiHatalari() {
        assertThat(siparisVer("akis-400", "AKTIVASYON", "5321234567", null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(siparisVer("akis-404", "AKTIVASYON", "905499999999", null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(http.getForEntity("/api/siparisler/999999999", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void kullanimKalanKotayiDusurur() {
        String msisdn = yeniHat("AKTIF", GNC10, 10240, SIMDI.minusDays(10));

        JsonNode hat = http.postForObject("/api/hatlar/" + msisdn + "/kullanim", Map.of("mb", 1240.5), JsonNode.class);

        assertThat(hat.get("kalanKotaMb").decimalValue()).isEqualByComparingTo("8999.5");
    }

    private ResponseEntity<JsonNode> siparisVer(String ref, String tip, String msisdn, String hedef) {
        Map<String, Object> govde = new java.util.HashMap<>(Map.of("istemciRef", ref, "tip", tip, "msisdn", msisdn));
        if (hedef != null) {
            govde.put("hedefTarifeKodu", hedef);
        }
        return http.postForEntity("/api/siparisler", govde, JsonNode.class);
    }

    private JsonNode sonuclanmasiniBekle(long id) {
        return await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100))
                .until(() -> http.getForObject("/api/siparisler/" + id, JsonNode.class),
                        s -> !"ALINDI".equals(s.get("durum").asText()));
    }
}
