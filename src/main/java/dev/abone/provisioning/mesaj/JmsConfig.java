package dev.abone.provisioning.mesaj;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

@Configuration
@EnableJms
public class JmsConfig {

    /** Mesajlar JSON TextMessage; ObjectMessage serilestirme riskine girmiyoruz. */
    @Bean
    MessageConverter jsonMesajCevirici(ObjectMapper objectMapper) {
        MappingJackson2MessageConverter c = new MappingJackson2MessageConverter();
        c.setTargetType(MessageType.TEXT);
        c.setTypeIdPropertyName("_tip");
        c.setTypeIdMappings(Map.of("siparis", SiparisMesaji.class));
        c.setObjectMapper(objectMapper);
        return c;
    }
}
