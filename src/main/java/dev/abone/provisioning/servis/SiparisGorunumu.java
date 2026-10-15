package dev.abone.provisioning.servis;

import dev.abone.provisioning.domain.Kanal;
import dev.abone.provisioning.domain.Siparis;
import dev.abone.provisioning.domain.SiparisDurumu;
import dev.abone.provisioning.domain.SiparisTipi;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Islem disina tasinabilir, lazy iliski icermeyen siparis gorunumu. */
public record SiparisGorunumu(
        Long id,
        String istemciRef,
        SiparisTipi tip,
        String msisdn,
        String hedefTarifeKodu,
        SiparisDurumu durum,
        Kanal kanal,
        String hataMesaji,
        BigDecimal kistUcret,
        BigDecimal devredenMb,
        LocalDateTime olusturma,
        LocalDateTime tamamlanma) {

    static SiparisGorunumu from(Siparis s) {
        return new SiparisGorunumu(
                s.getId(),
                s.getIstemciRef(),
                s.getTip(),
                s.getHat().getMsisdn(),
                s.getHedefTarife() == null ? null : s.getHedefTarife().getKod(),
                s.getDurum(),
                s.getKanal(),
                s.getHataMesaji(),
                s.getKistUcret(),
                s.getDevredenMb(),
                s.getOlusturma(),
                s.getTamamlanma());
    }

    public boolean sonuclandi() {
        return durum != SiparisDurumu.ALINDI;
    }
}
