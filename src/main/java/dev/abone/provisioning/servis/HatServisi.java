package dev.abone.provisioning.servis;

import dev.abone.provisioning.domain.FaturaKalemi;
import dev.abone.provisioning.domain.FaturaKalemiRepository;
import dev.abone.provisioning.domain.Hat;
import dev.abone.provisioning.domain.HatDurumu;
import dev.abone.provisioning.domain.HatRepository;
import dev.abone.provisioning.domain.Tarife;
import dev.abone.provisioning.domain.TarifeRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HatServisi {

    public record HatOzeti(String msisdn, String aboneAdi, HatDurumu durum, String tarifeKodu, String tarifeAdi,
                           BigDecimal aylikUcret, LocalDate aktivasyonTarihi, BigDecimal kotaMb,
                           BigDecimal kalanKotaMb) {
    }

    public record TarifeOzeti(String kod, String ad, BigDecimal aylikUcret, int dataMb, int dakika) {

        static TarifeOzeti from(Tarife t) {
            return new TarifeOzeti(t.getKod(), t.getAd(), t.getAylikUcret(), t.getDataMb(), t.getDakika());
        }
    }

    public record FaturaKalemiOzeti(String donem, String aciklama, BigDecimal tutar, LocalDateTime olusturma) {

        static FaturaKalemiOzeti from(FaturaKalemi k) {
            return new FaturaKalemiOzeti(k.getDonem(), k.getAciklama(), k.getTutar(), k.getOlusturma());
        }
    }

    private final HatRepository hatlar;
    private final TarifeRepository tarifeler;
    private final FaturaKalemiRepository faturaKalemleri;
    private final FaturaDao fatura;
    private final Clock saat;

    public HatServisi(HatRepository hatlar, TarifeRepository tarifeler, FaturaKalemiRepository faturaKalemleri,
                      FaturaDao fatura, Clock saat) {
        this.hatlar = hatlar;
        this.tarifeler = tarifeler;
        this.faturaKalemleri = faturaKalemleri;
        this.fatura = fatura;
        this.saat = saat;
    }

    @Transactional(readOnly = true)
    public HatOzeti ozet(String msisdn) {
        Hat hat = hatBul(msisdn);
        Tarife t = hat.getTarife();
        return new HatOzeti(hat.getMsisdn(), hat.getAbone().getAdSoyad(), hat.getDurum(), t.getKod(), t.getAd(),
                t.getAylikUcret(), hat.getAktivasyonTarihi(), hat.getKotaMb(), fatura.kalanKota(hat.getId()));
    }

    @Transactional
    public HatOzeti kullanimEkle(String msisdn, BigDecimal mb) {
        Hat hat = hatBul(msisdn);
        fatura.kullanimEkle(hat.getId(), mb, Zaman.simdi(saat));
        return ozet(msisdn);
    }

    @Transactional(readOnly = true)
    public List<FaturaKalemiOzeti> faturaKalemleri(String msisdn) {
        return faturaKalemleri.findByHatIdOrderById(hatBul(msisdn).getId()).stream()
                .map(FaturaKalemiOzeti::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TarifeOzeti> satistakiTarifeler() {
        return tarifeler.findByAktifOrderByAylikUcret(true).stream().map(TarifeOzeti::from).toList();
    }

    private Hat hatBul(String msisdn) {
        return hatlar.findByMsisdn(msisdn).orElseThrow(() -> new Hatalar.KayitBulunamadi("Hat bulunamadi: " + msisdn));
    }
}
