package dev.abone.provisioning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AltyapiOzellikleri(Oracle oracle, Artemis artemis, Jta jta) {

    public record Oracle(String url, String kullanici, String sifre, int havuz) {
    }

    public record Artemis(String url, String kullanici, String sifre, int havuz) {
    }

    /**
     * @param tmAdi     her JVM icin benzersiz olmali; XA kurtarma log'u bu adla eslesir
     * @param logDizini Atomikos islem log'u; yeniden baslatmada yarim kalan 2PC'ler buradan tamamlanir
     */
    public record Jta(String tmAdi, String logDizini, int zamanAsimiSn) {
    }
}
