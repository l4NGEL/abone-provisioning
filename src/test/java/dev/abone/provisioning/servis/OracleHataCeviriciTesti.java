package dev.abone.provisioning.servis;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.UncategorizedSQLException;

/** Kapsayici gerektirmeyen birim testi. */
class OracleHataCeviriciTesti {

    @Test
    void kullaniciHatasiIlkSatirdanAyiklanir() {
        SQLException ora = new SQLException("""
                ORA-20001: Hat zaten bu tarifede
                ORA-06512: at "ABONE.PKG_FATURA", line 132
                ORA-06512: at line 1
                https://docs.oracle.com/error-help/db/ora-20001/""", "72000", 20001);

        var ihlal = OracleHataCevirici.isKurali(new UncategorizedSQLException("cagri", "{call ...}", ora));

        assertThat(ihlal).hasValueSatisfying(e -> {
            assertThat(e.getOracleKodu()).isEqualTo(20001);
            assertThat(e.getMessage()).isEqualTo("Hat zaten bu tarifede");
        });
    }

    @Test
    void sistemHatasiIsKuraliSayilmaz() {
        SQLException kilit = new SQLException("ORA-00060: deadlock detected while waiting for resource", "61000", 60);

        assertThat(OracleHataCevirici.isKurali(new UncategorizedSQLException("cagri", "x", kilit))).isEmpty();
    }

    @Test
    void bosMesajGuvenli() {
        assertThat(OracleHataCevirici.sadeMesaj(null)).isEmpty();
    }
}
