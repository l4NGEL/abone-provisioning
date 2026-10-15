package dev.abone.provisioning.servis;

/** Servis katmaninin disariya verdigi hata turleri; REST ve SOAP katmanlari bunlari esler. */
public final class Hatalar {

    private Hatalar() {
    }

    /** Istenen hat/tarife/siparis yok. */
    public static class KayitBulunamadi extends RuntimeException {
        public KayitBulunamadi(String mesaj) {
            super(mesaj);
        }
    }

    /** Istek senkron on kontrolden gecemedi (ornegin aktif hatta aktivasyon). */
    public static class GecersizIstek extends RuntimeException {
        public GecersizIstek(String mesaj) {
            super(mesaj);
        }
    }

    /** Ayni istemci referansi farkli bir istek icin daha once kullanilmis. */
    public static class IstemciRefCakismasi extends RuntimeException {
        public IstemciRefCakismasi(String mesaj) {
            super(mesaj);
        }
    }

    /** PL/SQL tarafinin ORA-20000..20999 ile reddettigi is kurali. */
    public static class IsKuraliIhlali extends RuntimeException {
        private final int oracleKodu;

        public IsKuraliIhlali(int oracleKodu, String mesaj, Throwable sebep) {
            super(mesaj, sebep);
            this.oracleKodu = oracleKodu;
        }

        public int getOracleKodu() {
            return oracleKodu;
        }
    }
}
