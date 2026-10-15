package dev.abone.provisioning.web;

import jakarta.el.ELContext;
import jakarta.el.ELException;
import jakarta.el.ELResolver;
import jakarta.el.PropertyNotWritableException;
import java.lang.reflect.RecordComponent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Jakarta EL 5.0 (Faces 4.0) record bilesenlerini JavaBean ozelligi olarak gormez:
 * #{s.id} getId() arar. Servis katmani record dondurdugu icin erisimci metotlari burada
 * cozulur. EL 6.0 (Jakarta EE 11) ile gereksiz hale gelir.
 */
public class RecordElCozucu extends ELResolver {

    private final Map<Class<?>, Map<String, RecordComponent>> bilesenler = new ConcurrentHashMap<>();

    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        RecordComponent bilesen = bilesen(base, property);
        if (bilesen == null) {
            return null;
        }
        try {
            Object deger = bilesen.getAccessor().invoke(base);
            context.setPropertyResolved(base, property);
            return deger;
        } catch (ReflectiveOperationException e) {
            throw new ELException(e);
        }
    }

    @Override
    public Class<?> getType(ELContext context, Object base, Object property) {
        RecordComponent bilesen = bilesen(base, property);
        if (bilesen == null) {
            return null;
        }
        context.setPropertyResolved(base, property);
        // record degistirilemez; JSF spesifikasyonu salt okunur ozellik icin null bekler
        return null;
    }

    @Override
    public void setValue(ELContext context, Object base, Object property, Object value) {
        if (bilesen(base, property) != null) {
            throw new PropertyNotWritableException(base.getClass().getSimpleName() + " degistirilemez bir record");
        }
    }

    @Override
    public boolean isReadOnly(ELContext context, Object base, Object property) {
        if (bilesen(base, property) == null) {
            return false;
        }
        context.setPropertyResolved(base, property);
        return true;
    }

    @Override
    public Class<?> getCommonPropertyType(ELContext context, Object base) {
        return base instanceof Record ? String.class : null;
    }

    private RecordComponent bilesen(Object base, Object property) {
        if (!(base instanceof Record) || !(property instanceof String ad)) {
            return null;
        }
        return bilesenler.computeIfAbsent(base.getClass(), RecordElCozucu::tara).get(ad);
    }

    private static Map<String, RecordComponent> tara(Class<?> tip) {
        Map<String, RecordComponent> harita = new ConcurrentHashMap<>();
        for (RecordComponent rc : tip.getRecordComponents()) {
            rc.getAccessor().setAccessible(true);
            harita.put(rc.getName(), rc);
        }
        return harita;
    }
}
