package dev.abone.provisioning.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Eski sistemlerden kalma CHAR(1) 'E'/'H' bayraklari. */
@Converter
public class EvetHayirCevirici implements AttributeConverter<Boolean, String> {

    @Override
    public String convertToDatabaseColumn(Boolean deger) {
        return Boolean.TRUE.equals(deger) ? "E" : "H";
    }

    @Override
    public Boolean convertToEntityAttribute(String kolon) {
        return "E".equals(kolon);
    }
}
