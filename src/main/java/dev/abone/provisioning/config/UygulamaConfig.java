package dev.abone.provisioning.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class UygulamaConfig implements WebMvcConfigurer {

    public static final ZoneId BOLGE = ZoneId.of("Europe/Istanbul");

    /** Kist ucret gun hesabi yerel takvim gunune gore yapilir; testler sabit saat verir. */
    @Bean
    Clock saat() {
        return Clock.system(BOLGE);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/operator.xhtml");
    }
}
