package dev.abone.provisioning.servis;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

final class Zaman {

    private Zaman() {
    }

    /** Oracle TIMESTAMP mikro saniye tutar; API yaniti ile DB'deki deger ayni olsun. */
    static LocalDateTime simdi(Clock saat) {
        return LocalDateTime.now(saat).truncatedTo(ChronoUnit.MICROS);
    }
}
