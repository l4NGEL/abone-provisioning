package dev.abone.provisioning;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.activemq.ArtemisContainer;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.MountableFile;

/**
 * Gercek Oracle 23ai Free ve Artemis kapsayicilariyla calisan testlerin tabani. Kapsayicilar
 * JVM basina bir kez kalkar; Spring baglami tum alt siniflarda ayni oldugu icin onbellekten
 * paylasilir. Her test kendi hattini olusturur, bu yuzden test sirasi onemli degildir.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(EntegrasyonTesti.SabitSaat.class)
public abstract class EntegrasyonTesti {

    /** 31 gunluk ayin 11'i: kalan gun orani 21/31. */
    protected static final LocalDateTime SIMDI = LocalDateTime.of(2026, 10, 11, 10, 0);

    static final OracleContainer ORACLE = new OracleContainer("gvenzl/oracle-free:23-slim-faststart")
            .withUsername("abone")
            .withPassword("abone")
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("db/init/01_xa_yetkileri.sql")),
                    "/container-entrypoint-initdb.d/01_xa_yetkileri.sql");

    static final ArtemisContainer ARTEMIS = new ArtemisContainer("apache/activemq-artemis:2.42.0-alpine")
            .withUser("artemis")
            .withPassword("artemis");

    static {
        Startables.deepStart(ORACLE, ARTEMIS).join();
    }

    private static final AtomicInteger HAT_SAYACI = new AtomicInteger();

    @DynamicPropertySource
    static void altyapi(DynamicPropertyRegistry r) {
        r.add("app.oracle.url", ORACLE::getJdbcUrl);
        r.add("app.oracle.kullanici", ORACLE::getUsername);
        r.add("app.oracle.sifre", ORACLE::getPassword);
        r.add("app.artemis.url", ARTEMIS::getBrokerUrl);
        r.add("app.artemis.kullanici", ARTEMIS::getUser);
        r.add("app.artemis.sifre", ARTEMIS::getPassword);
        r.add("app.jta.log-dizini", () -> "target/atomikos-test");
        r.add("app.jta.tm-adi", () -> "abone-prov-test");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SabitSaat {
        @Bean
        @Primary
        Clock sabitSaat() {
            ZoneId bolge = ZoneId.of("Europe/Istanbul");
            return Clock.fixed(SIMDI.atZone(bolge).toInstant(), bolge);
        }
    }

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected TransactionTemplate islem;

    /**
     * Teste ozel hat. Testler paralel degil ama ayni baglami paylasiyor; benzersiz MSISDN'ler
     * 90541xxxxxxx araligindan uretilir (ornek veri 9053xxxxxxxx kullanir).
     */
    protected String yeniHat(String durum, int tarifeId, double kotaMb, LocalDateTime kotaBaslangic) {
        String msisdn = "90541" + String.format("%07d", HAT_SAYACI.incrementAndGet());
        islem.executeWithoutResult(s -> jdbc.update("""
                INSERT INTO HAT (ID, MSISDN, ABONE_ID, TARIFE_ID, DURUM, AKTIVASYON_TARIHI, KOTA_MB, KOTA_BASLANGIC)
                VALUES (SEQ_HAT.NEXTVAL, ?, 1, ?, ?, ?, ?, ?)""",
                msisdn, tarifeId, durum,
                "AKTIF".equals(durum) ? java.sql.Date.valueOf("2026-01-01") : null,
                kotaMb,
                kotaBaslangic == null ? null : java.sql.Timestamp.valueOf(kotaBaslangic)));
        return msisdn;
    }

    protected long hatId(String msisdn) {
        return jdbc.queryForObject("SELECT ID FROM HAT WHERE MSISDN = ?", Long.class, msisdn);
    }

    protected void kullanimYaz(String msisdn, double mb, LocalDateTime zaman) {
        islem.executeWithoutResult(s -> jdbc.update(
                "INSERT INTO KULLANIM (ID, HAT_ID, DATA_MB, KAYIT_ZAMANI) VALUES (SEQ_KULLANIM.NEXTVAL, ?, ?, ?)",
                hatId(msisdn), mb, java.sql.Timestamp.valueOf(zaman)));
    }

    protected static final int EKO5 = 1;
    protected static final int GNC10 = 2;
    protected static final int PLT20 = 3;
    protected static final int ESKI4 = 4;
}
