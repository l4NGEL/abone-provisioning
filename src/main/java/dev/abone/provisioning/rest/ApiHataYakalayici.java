package dev.abone.provisioning.rest;

import dev.abone.provisioning.servis.Hatalar;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** RFC 9457 problem+json. Dogrulama hatalari (400) Spring'in varsayilan isleyicisinde kalir. */
@RestControllerAdvice(basePackageClasses = ApiHataYakalayici.class)
public class ApiHataYakalayici {

    @ExceptionHandler(Hatalar.KayitBulunamadi.class)
    ProblemDetail bulunamadi(Hatalar.KayitBulunamadi e) {
        return problem(HttpStatus.NOT_FOUND, "Kayit bulunamadi", e.getMessage());
    }

    @ExceptionHandler(Hatalar.GecersizIstek.class)
    ProblemDetail gecersiz(Hatalar.GecersizIstek e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Istek is kuralina uymuyor", e.getMessage());
    }

    @ExceptionHandler(Hatalar.IsKuraliIhlali.class)
    ProblemDetail isKurali(Hatalar.IsKuraliIhlali e) {
        ProblemDetail p = problem(HttpStatus.UNPROCESSABLE_ENTITY, "Istek is kuralina uymuyor", e.getMessage());
        p.setProperty("oracleKodu", e.getOracleKodu());
        return p;
    }

    @ExceptionHandler(Hatalar.IstemciRefCakismasi.class)
    ProblemDetail cakisma(Hatalar.IstemciRefCakismasi e) {
        return problem(HttpStatus.CONFLICT, "istemciRef cakismasi", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus durum, String baslik, String detay) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(durum, detay);
        p.setTitle(baslik);
        return p;
    }
}
