package dev.abone.provisioning.servis;

import java.sql.SQLException;
import java.util.Optional;

/**
 * RAISE_APPLICATION_ERROR ile gelen is kurali hatalarini ayiklar. Oracle mesaji
 * "ORA-20001: Hat zaten bu tarifede\nORA-06512: at ..." bicimindedir; yalnizca ilk satirin
 * kullaniciya yonelik kismi alinir.
 */
final class OracleHataCevirici {

    static final int KULLANICI_HATA_ALT = 20000;
    static final int KULLANICI_HATA_UST = 20999;

    private OracleHataCevirici() {
    }

    static Optional<Hatalar.IsKuraliIhlali> isKurali(Throwable hata) {
        for (Throwable t = hata; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql
                    && sql.getErrorCode() >= KULLANICI_HATA_ALT && sql.getErrorCode() <= KULLANICI_HATA_UST) {
                return Optional.of(new Hatalar.IsKuraliIhlali(sql.getErrorCode(), sadeMesaj(sql.getMessage()), hata));
            }
        }
        return Optional.empty();
    }

    static String sadeMesaj(String oracleMesaji) {
        if (oracleMesaji == null) {
            return "";
        }
        String ilkSatir = oracleMesaji.lines().findFirst().orElse("").strip();
        return ilkSatir.replaceFirst("^ORA-\\d{5}:\\s*", "");
    }
}
