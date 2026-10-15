package dev.abone.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.abone.provisioning.servis.FaturaDao;
import dev.abone.provisioning.servis.Hatalar;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** PKG_FATURA'nin hesaplari ve is kurali hatalari, Java tarafindan cagrildigi haliyle. */
class PkgFaturaTesti extends EntegrasyonTesti {

    @Autowired
    FaturaDao fatura;

    @Test
    void paketGecerliDerlenmis() {
        // Flyway derleme hatasini yalnizca uyari olarak gecer; INVALID paket burada yakalanir
        List<String> gecersiz = jdbc.queryForList(
                "SELECT OBJECT_NAME || ' ' || OBJECT_TYPE FROM USER_OBJECTS WHERE STATUS <> 'VALID'", String.class);
        assertThat(gecersiz).isEmpty();
    }

    @Test
    void kalanGunOraniAyinGunSayisinaGoreHesaplanir() {
        assertThat(oran("2026-10-11")).isEqualByComparingTo(bd(21.0 / 31));
        assertThat(oran("2026-10-01")).isEqualByComparingTo("1");
        assertThat(oran("2027-02-28")).isEqualByComparingTo(bd(1.0 / 28));
        assertThat(oran("2028-02-28")).isEqualByComparingTo(bd(2.0 / 29)); // artik yil
    }

    @Test
    void aktivasyonKalanGunlerIcinKistUcretVeKotaVerir() {
        String msisdn = yeniHat("BEKLEMEDE", GNC10, 0, null);

        FaturaDao.AktivasyonSonucu s = islem.execute(t -> fatura.hatAktiveEt(hatId(msisdn), SIMDI));

        // 250 * 21/31 = 169.354..., 10240 * 21/31 = 6936.774...
        assertThat(s.kistUcret()).isEqualByComparingTo("169.35");
        assertThat(s.kotaMb()).isEqualByComparingTo("6936.77");
        Map<String, Object> hat = jdbc.queryForMap("SELECT DURUM, KOTA_MB, VERSIYON FROM HAT WHERE MSISDN = ?", msisdn);
        assertThat(hat.get("DURUM")).isEqualTo("AKTIF");
        assertThat((BigDecimal) hat.get("KOTA_MB")).isEqualByComparingTo("6936.77");
        assertThat(((BigDecimal) hat.get("VERSIYON")).intValue()).isEqualTo(1);
        assertThat(kalemler(msisdn)).containsExactly(new BigDecimal("169.35"));
    }

    @Test
    void tarifeYukseltmeFarkiKistlerVeKullanilmayanKotayiDevreder() {
        String msisdn = yeniHat("AKTIF", GNC10, 10240, LocalDateTime.of(2026, 10, 1, 0, 0));
        kullanimYaz(msisdn, 3000, LocalDateTime.of(2026, 10, 5, 12, 0));
        kullanimYaz(msisdn, 500, LocalDateTime.of(2026, 9, 20, 12, 0)); // kota baslangicindan once, sayilmaz

        FaturaDao.TarifeDegisimSonucu s = islem.execute(t -> fatura.tarifeDegistir(hatId(msisdn), PLT20, SIMDI));

        // iade -250*21/31 = -169.35, yeni 450*21/31 = 304.84 -> net 135.49
        assertThat(s.kistUcret()).isEqualByComparingTo("135.49");
        assertThat(s.devredenMb()).isEqualByComparingTo("7240");
        assertThat(kalemler(msisdn)).containsExactly(new BigDecimal("-169.35"), new BigDecimal("304.84"));
        // yeni donem kotasi: 20480*21/31 = 13873.55 + 7240 devreden; degisiklik aninda hic kullanilmamis
        assertThat(fatura.kalanKota(hatId(msisdn))).isEqualByComparingTo("21113.55");
    }

    @Test
    void tarifeDusurmedeNetTutarNegatifVeDevredenYeniTarifeKotasiylaSinirli() {
        String msisdn = yeniHat("AKTIF", PLT20, 20480, LocalDateTime.of(2026, 10, 1, 0, 0));

        FaturaDao.TarifeDegisimSonucu s = islem.execute(t -> fatura.tarifeDegistir(hatId(msisdn), EKO5, SIMDI));

        // -450*21/31 + 180*21/31 = -304.84 + 121.94
        assertThat(s.kistUcret()).isEqualByComparingTo("-182.90");
        assertThat(s.devredenMb()).isEqualByComparingTo("5120");
    }

    @Test
    void kalanKotaSifirinAltinaInmez() {
        String msisdn = yeniHat("AKTIF", EKO5, 1000, LocalDateTime.of(2026, 10, 1, 0, 0));
        kullanimYaz(msisdn, 1500, LocalDateTime.of(2026, 10, 2, 0, 0));

        assertThat(fatura.kalanKota(hatId(msisdn))).isEqualByComparingTo("0");
    }

    @Test
    void ayniTarifeyeGecisReddedilir() {
        String msisdn = yeniHat("AKTIF", GNC10, 10240, SIMDI.minusDays(5));

        assertThatThrownBy(() -> islem.execute(t -> fatura.tarifeDegistir(hatId(msisdn), GNC10, SIMDI)))
                .isInstanceOf(Hatalar.IsKuraliIhlali.class)
                .hasMessage("Hat zaten bu tarifede")
                .extracting("oracleKodu").isEqualTo(20001);
    }

    @Test
    void beklemedekiHattaTarifeDegisikligiReddedilir() {
        String msisdn = yeniHat("BEKLEMEDE", GNC10, 0, null);

        assertThatThrownBy(() -> islem.execute(t -> fatura.tarifeDegistir(hatId(msisdn), PLT20, SIMDI)))
                .isInstanceOf(Hatalar.IsKuraliIhlali.class)
                .hasMessageContaining("AKTIF olmali")
                .extracting("oracleKodu").isEqualTo(20002);
    }

    @Test
    void satistanKalkmisTarifeyeGecilemez() {
        String msisdn = yeniHat("AKTIF", GNC10, 10240, SIMDI.minusDays(5));

        assertThatThrownBy(() -> islem.execute(t -> fatura.tarifeDegistir(hatId(msisdn), ESKI4, SIMDI)))
                .isInstanceOf(Hatalar.IsKuraliIhlali.class)
                .extracting("oracleKodu").isEqualTo(20003);
    }

    @Test
    void reddedilenCagriHatSatiriniDegistirmez() {
        String msisdn = yeniHat("AKTIF", GNC10, 10240, SIMDI.minusDays(5));
        int onceki = jdbc.queryForObject("SELECT VERSIYON FROM HAT WHERE MSISDN = ?", Integer.class, msisdn);

        assertThatThrownBy(() -> islem.execute(t -> fatura.tarifeDegistir(hatId(msisdn), GNC10, SIMDI)))
                .isInstanceOf(Hatalar.IsKuraliIhlali.class);

        assertThat(jdbc.queryForObject("SELECT VERSIYON FROM HAT WHERE MSISDN = ?", Integer.class, msisdn))
                .isEqualTo(onceki);
        assertThat(kalemler(msisdn)).isEmpty();
    }

    private BigDecimal oran(String tarih) {
        return jdbc.queryForObject("SELECT PKG_FATURA.KALAN_GUN_ORANI(DATE '" + tarih + "') FROM DUAL", BigDecimal.class)
                .setScale(10, java.math.RoundingMode.HALF_UP);
    }

    private List<BigDecimal> kalemler(String msisdn) {
        return jdbc.queryForList("SELECT TUTAR FROM FATURA_KALEMI WHERE HAT_ID = ? ORDER BY ID",
                BigDecimal.class, hatId(msisdn));
    }

    private static BigDecimal bd(double d) {
        return new BigDecimal(d).setScale(10, java.math.RoundingMode.HALF_UP);
    }
}
