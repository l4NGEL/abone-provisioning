package dev.abone.provisioning;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** JSF sayfasi render ediliyor mu ve record'lar EL'den okunabiliyor mu. */
class OperatorEkraniTesti extends EntegrasyonTesti {

    @Autowired
    TestRestTemplate http;

    @Test
    void operatorSayfasiSonSiparislerleRenderEdilir() {
        ResponseEntity<String> sayfa = http.getForEntity("/operator.xhtml", String.class);

        assertThat(sayfa.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sayfa.getBody())
                .contains("Hat sorgula")
                .contains("Son siparişler")
                .contains("jakarta.faces.ViewState")
                .contains("primefaces");
    }

    @Test
    void kokAdresOperatorEkraninaYonlendirir() {
        ResponseEntity<String> yanit = http.getForEntity("/", String.class);

        assertThat(yanit.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(yanit.getBody()).contains("Hat İşlemleri");
    }
}
