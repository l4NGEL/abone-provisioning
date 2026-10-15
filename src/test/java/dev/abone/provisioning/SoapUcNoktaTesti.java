package dev.abone.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import dev.abone.provisioning.soap.model.HatSorgulaRequest;
import dev.abone.provisioning.soap.model.HatSorgulaResponse;
import dev.abone.provisioning.soap.model.SiparisOlusturRequest;
import dev.abone.provisioning.soap.model.SiparisOlusturResponse;
import dev.abone.provisioning.soap.model.SiparisSorgulaRequest;
import dev.abone.provisioning.soap.model.SiparisSorgulaResponse;
import dev.abone.provisioning.soap.model.SiparisTipi;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.client.SoapFaultClientException;

/** SOAP istemcisi, uretilen JAXB siniflariyla gercek HTTP uzerinden konusur. */
class SoapUcNoktaTesti extends EntegrasyonTesti {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate http;

    WebServiceTemplate soap;

    @BeforeEach
    void istemci() throws Exception {
        Jaxb2Marshaller m = new Jaxb2Marshaller();
        m.setContextPath("dev.abone.provisioning.soap.model");
        m.afterPropertiesSet();
        soap = new WebServiceTemplate(m);
        soap.setDefaultUri("http://localhost:" + port + "/ws");
    }

    @Test
    void wsdlXsddenUretilirVeUcIslemiIcerir() {
        String wsdl = http.getForObject("/ws/provisioning.wsdl", String.class);

        assertThat(wsdl)
                .contains("wsdl:operation name=\"SiparisOlustur\"")
                .contains("wsdl:operation name=\"SiparisSorgula\"")
                .contains("wsdl:operation name=\"HatSorgula\"")
                .contains("location=\"http://localhost:" + port + "/ws\"");
    }

    @Test
    void soapIleTarifeDegisikligiVeSorgulama() {
        String msisdn = yeniHat("AKTIF", EKO5, 5120, SIMDI.minusDays(2));
        SiparisOlusturRequest istek = new SiparisOlusturRequest();
        istek.setIstemciRef("soap-1");
        istek.setTip(SiparisTipi.TARIFE_DEGISIKLIGI);
        istek.setMsisdn(msisdn);
        istek.setHedefTarifeKodu("GNC10");

        SiparisOlusturResponse yanit = (SiparisOlusturResponse) soap.marshalSendAndReceive(istek);
        assertThat(yanit.isTekrarIstek()).isFalse();
        long id = yanit.getSiparis().getSiparisId();

        SiparisSorgulaRequest sorgu = new SiparisSorgulaRequest();
        sorgu.setSiparisId(id);
        SiparisSorgulaResponse sonuc = await().atMost(Duration.ofSeconds(20))
                .until(() -> (SiparisSorgulaResponse) soap.marshalSendAndReceive(sorgu),
                        s -> !"ALINDI".equals(s.getSiparis().getDurum()));

        assertThat(sonuc.getSiparis().getDurum()).isEqualTo("TAMAMLANDI");
        // (250 - 180) * 21/31: -121.94 + 169.35
        assertThat(sonuc.getSiparis().getKistUcret()).isEqualByComparingTo("47.41");
        assertThat(jdbc.queryForObject("SELECT KANAL FROM SIPARIS WHERE ID = ?", String.class, id)).isEqualTo("SOAP");

        HatSorgulaRequest hatSorgu = new HatSorgulaRequest();
        hatSorgu.setMsisdn(msisdn);
        HatSorgulaResponse hat = (HatSorgulaResponse) soap.marshalSendAndReceive(hatSorgu);
        assertThat(hat.getTarifeKodu()).isEqualTo("GNC10");
    }

    @Test
    void bilinmeyenHatClientFaultDoner() {
        HatSorgulaRequest istek = new HatSorgulaRequest();
        istek.setMsisdn("905499999998");

        assertThatThrownBy(() -> soap.marshalSendAndReceive(istek))
                .isInstanceOf(SoapFaultClientException.class)
                .hasMessageContaining("Hat bulunamadi")
                .satisfies(e -> assertThat(((SoapFaultClientException) e).getFaultCode().getLocalPart())
                        .isEqualTo("Client"));
    }
}
