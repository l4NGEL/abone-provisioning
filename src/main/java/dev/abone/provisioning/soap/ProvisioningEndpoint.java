package dev.abone.provisioning.soap;

import dev.abone.provisioning.domain.Kanal;
import dev.abone.provisioning.domain.SiparisTipi;
import dev.abone.provisioning.servis.HatServisi;
import dev.abone.provisioning.servis.SiparisGorunumu;
import dev.abone.provisioning.servis.SiparisKomutu;
import dev.abone.provisioning.servis.SiparisServisi;
import dev.abone.provisioning.soap.model.HatSorgulaRequest;
import dev.abone.provisioning.soap.model.HatSorgulaResponse;
import dev.abone.provisioning.soap.model.Siparis;
import dev.abone.provisioning.soap.model.SiparisOlusturRequest;
import dev.abone.provisioning.soap.model.SiparisOlusturResponse;
import dev.abone.provisioning.soap.model.SiparisSorgulaRequest;
import dev.abone.provisioning.soap.model.SiparisSorgulaResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

/** REST ile ayni servis metotlari; yalnizca tasima bicimi farkli. */
@Endpoint
public class ProvisioningEndpoint {

    private static final DatatypeFactory XML_TARIH = DatatypeFactory.newDefaultInstance();
    // xs:dateTime saniyeyi zorunlu tutar; LocalDateTime.toString() saniye 0 iken onu atar
    private static final DateTimeFormatter XS_DATETIME = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS");

    private final SiparisServisi siparisler;
    private final HatServisi hatlar;

    public ProvisioningEndpoint(SiparisServisi siparisler, HatServisi hatlar) {
        this.siparisler = siparisler;
        this.hatlar = hatlar;
    }

    @PayloadRoot(namespace = WebServiceConfig.NS, localPart = "SiparisOlusturRequest")
    @ResponsePayload
    public SiparisOlusturResponse siparisOlustur(@RequestPayload SiparisOlusturRequest istek) {
        SiparisServisi.Sonuc sonuc = siparisler.olustur(new SiparisKomutu(
                istek.getIstemciRef(), SiparisTipi.valueOf(istek.getTip().value()), istek.getMsisdn(),
                istek.getHedefTarifeKodu()), Kanal.SOAP);
        SiparisOlusturResponse yanit = new SiparisOlusturResponse();
        yanit.setSiparis(xml(sonuc.siparis()));
        yanit.setTekrarIstek(sonuc.tekrarIstek());
        return yanit;
    }

    @PayloadRoot(namespace = WebServiceConfig.NS, localPart = "SiparisSorgulaRequest")
    @ResponsePayload
    public SiparisSorgulaResponse siparisSorgula(@RequestPayload SiparisSorgulaRequest istek) {
        SiparisSorgulaResponse yanit = new SiparisSorgulaResponse();
        yanit.setSiparis(xml(siparisler.bul(istek.getSiparisId())));
        return yanit;
    }

    @PayloadRoot(namespace = WebServiceConfig.NS, localPart = "HatSorgulaRequest")
    @ResponsePayload
    public HatSorgulaResponse hatSorgula(@RequestPayload HatSorgulaRequest istek) {
        HatServisi.HatOzeti ozet = hatlar.ozet(istek.getMsisdn());
        HatSorgulaResponse yanit = new HatSorgulaResponse();
        yanit.setMsisdn(ozet.msisdn());
        yanit.setAboneAdi(ozet.aboneAdi());
        yanit.setDurum(ozet.durum().name());
        yanit.setTarifeKodu(ozet.tarifeKodu());
        yanit.setTarifeAdi(ozet.tarifeAdi());
        yanit.setKotaMb(ozet.kotaMb());
        yanit.setKalanKotaMb(ozet.kalanKotaMb());
        return yanit;
    }

    private static Siparis xml(SiparisGorunumu s) {
        Siparis x = new Siparis();
        x.setSiparisId(s.id());
        x.setIstemciRef(s.istemciRef());
        x.setTip(dev.abone.provisioning.soap.model.SiparisTipi.fromValue(s.tip().name()));
        x.setMsisdn(s.msisdn());
        x.setHedefTarifeKodu(s.hedefTarifeKodu());
        x.setDurum(s.durum().name());
        x.setHataMesaji(s.hataMesaji());
        x.setKistUcret(s.kistUcret());
        x.setDevredenMb(s.devredenMb());
        x.setOlusturma(xml(s.olusturma()));
        x.setTamamlanma(s.tamamlanma() == null ? null : xml(s.tamamlanma()));
        return x;
    }

    private static XMLGregorianCalendar xml(LocalDateTime zaman) {
        return XML_TARIH.newXMLGregorianCalendar(XS_DATETIME.format(zaman));
    }
}
