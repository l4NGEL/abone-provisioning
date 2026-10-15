package dev.abone.provisioning.soap;

import dev.abone.provisioning.servis.Hatalar;
import java.util.Properties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.soap.server.endpoint.SoapFaultDefinition;
import org.springframework.ws.soap.server.endpoint.SoapFaultMappingExceptionResolver;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

/**
 * Contract-first SOAP: XSD tek kaynak, JAXB siniflari derlemede uretilir, WSDL calisma
 * zamaninda XSD'den turetilir. WSDL adresi: /ws/provisioning.wsdl
 */
@Configuration
public class WebServiceConfig {

    public static final String NS = "urn:abone:provisioning:1";

    @Bean
    XsdSchema provisioningSema() {
        return new SimpleXsdSchema(new ClassPathResource("xsd/provisioning.xsd"));
    }

    @Bean(name = "provisioning")
    DefaultWsdl11Definition provisioningWsdl(XsdSchema provisioningSema) {
        DefaultWsdl11Definition wsdl = new DefaultWsdl11Definition();
        wsdl.setPortTypeName("ProvisioningPort");
        wsdl.setServiceName("ProvisioningService");
        wsdl.setLocationUri("/ws");
        wsdl.setTargetNamespace(NS);
        wsdl.setSchema(provisioningSema);
        return wsdl;
    }

    /** Is hatalari Client fault, gerisi Server fault; faultstring istisna mesaji. */
    @Bean
    SoapFaultMappingExceptionResolver soapHataCozucu() {
        SoapFaultMappingExceptionResolver cozucu = new SoapFaultMappingExceptionResolver();
        Properties eslesme = new Properties();
        for (Class<?> istemciHatasi : new Class<?>[] {
                Hatalar.KayitBulunamadi.class, Hatalar.GecersizIstek.class,
                Hatalar.IsKuraliIhlali.class, Hatalar.IstemciRefCakismasi.class}) {
            eslesme.setProperty(istemciHatasi.getName(), "CLIENT");
        }
        cozucu.setExceptionMappings(eslesme);
        SoapFaultDefinition varsayilan = new SoapFaultDefinition();
        varsayilan.setFaultCode(SoapFaultDefinition.SERVER);
        cozucu.setDefaultFault(varsayilan);
        cozucu.setOrder(1);
        return cozucu;
    }
}
