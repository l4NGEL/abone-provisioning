package dev.abone.provisioning.servis;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Repository;

/**
 * PKG_FATURA paketine erisim. JPA yerine JdbcTemplate kullaniliyor: RAISE_APPLICATION_ERROR
 * yalnizca o ifadeyi geri alir, ancak EntityManager uzerinden gelen bir hata Hibernate'in
 * JTA islemini rollback-only isaretlemesine yol acar ve siparisi REDDEDILDI olarak
 * kaydedemezdik.
 */
@Repository
public class FaturaDao {

    public record AktivasyonSonucu(BigDecimal kistUcret, BigDecimal kotaMb) {
    }

    public record TarifeDegisimSonucu(BigDecimal kistUcret, BigDecimal devredenMb) {
    }

    private final JdbcTemplate jdbc;
    private final SimpleJdbcCall hatAktiveEt;
    private final SimpleJdbcCall tarifeDegistir;
    private final SimpleJdbcCall kullanimEkle;

    public FaturaDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        // Metadata sorgusu kapali: parametre sirasi bildirildigi gibi, her cagrida ek round-trip yok
        this.hatAktiveEt = paketCagrisi("HAT_AKTIVE_ET",
                new SqlParameter("p_hat_id", Types.NUMERIC),
                new SqlParameter("p_zaman", Types.TIMESTAMP),
                new SqlOutParameter("o_kist_ucret", Types.NUMERIC),
                new SqlOutParameter("o_kota_mb", Types.NUMERIC));
        this.tarifeDegistir = paketCagrisi("TARIFE_DEGISTIR",
                new SqlParameter("p_hat_id", Types.NUMERIC),
                new SqlParameter("p_yeni_tarife_id", Types.NUMERIC),
                new SqlParameter("p_zaman", Types.TIMESTAMP),
                new SqlOutParameter("o_kist_ucret", Types.NUMERIC),
                new SqlOutParameter("o_devreden_mb", Types.NUMERIC));
        this.kullanimEkle = paketCagrisi("KULLANIM_EKLE",
                new SqlParameter("p_hat_id", Types.NUMERIC),
                new SqlParameter("p_mb", Types.NUMERIC),
                new SqlParameter("p_zaman", Types.TIMESTAMP));
    }

    private SimpleJdbcCall paketCagrisi(String prosedur, SqlParameter... parametreler) {
        return new SimpleJdbcCall(jdbc)
                .withCatalogName("PKG_FATURA")
                .withProcedureName(prosedur)
                .withoutProcedureColumnMetaDataAccess()
                .declareParameters(parametreler);
    }

    public AktivasyonSonucu hatAktiveEt(long hatId, LocalDateTime zaman) {
        Map<String, Object> sonuc = cevir(() -> hatAktiveEt.execute(Map.of(
                "p_hat_id", hatId,
                "p_zaman", Timestamp.valueOf(zaman))));
        return new AktivasyonSonucu((BigDecimal) sonuc.get("o_kist_ucret"), (BigDecimal) sonuc.get("o_kota_mb"));
    }

    public TarifeDegisimSonucu tarifeDegistir(long hatId, int yeniTarifeId, LocalDateTime zaman) {
        Map<String, Object> sonuc = cevir(() -> tarifeDegistir.execute(Map.of(
                "p_hat_id", hatId,
                "p_yeni_tarife_id", yeniTarifeId,
                "p_zaman", Timestamp.valueOf(zaman))));
        return new TarifeDegisimSonucu((BigDecimal) sonuc.get("o_kist_ucret"), (BigDecimal) sonuc.get("o_devreden_mb"));
    }

    public void kullanimEkle(long hatId, BigDecimal mb, LocalDateTime zaman) {
        cevir(() -> kullanimEkle.execute(Map.of(
                "p_hat_id", hatId,
                "p_mb", mb,
                "p_zaman", Timestamp.valueOf(zaman))));
    }

    public BigDecimal kalanKota(long hatId) {
        return cevir(() -> jdbc.queryForObject("SELECT PKG_FATURA.KALAN_KOTA(?) FROM DUAL", BigDecimal.class, hatId));
    }

    private static <T> T cevir(Supplier<T> cagri) {
        try {
            return cagri.get();
        } catch (DataAccessException e) {
            throw OracleHataCevirici.isKurali(e).<RuntimeException>map(ihlal -> ihlal).orElse(e);
        }
    }
}
