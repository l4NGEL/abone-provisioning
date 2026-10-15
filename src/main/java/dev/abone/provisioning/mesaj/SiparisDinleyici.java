package dev.abone.provisioning.mesaj;

import dev.abone.provisioning.servis.ProvisioningServisi;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Listener container JTA islem yoneticisiyle calisir: mesajin tuketilmesi ve veritabani
 * guncellemeleri tek XA isleminde. Teknik hatada ikisi de geri alinir, mesaj yeniden teslim edilir.
 */
@Component
public class SiparisDinleyici {

    public static final String ID = "siparisDinleyici";

    private final ProvisioningServisi provisioning;

    public SiparisDinleyici(ProvisioningServisi provisioning) {
        this.provisioning = provisioning;
    }

    @JmsListener(id = ID, destination = Kuyruklar.SIPARIS, concurrency = "${app.jms.eszamanlilik:4-8}")
    public void al(SiparisMesaji mesaj, @Header(name = "JMSXDeliveryCount", defaultValue = "1") int teslimSayisi) {
        provisioning.isle(mesaj.siparisId(), teslimSayisi);
    }
}
