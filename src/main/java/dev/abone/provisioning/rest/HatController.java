package dev.abone.provisioning.rest;

import dev.abone.provisioning.servis.HatServisi;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HatController {

    /** Sebeke tarafindaki kullanim kaydini (CDR) taklit eder; kota hesabini denemek icin. */
    public record KullanimIstegi(@NotNull @DecimalMin("0.01") @DecimalMax("102400") BigDecimal mb) {
    }

    private final HatServisi hatlar;

    public HatController(HatServisi hatlar) {
        this.hatlar = hatlar;
    }

    @GetMapping("/hatlar/{msisdn}")
    public HatServisi.HatOzeti ozet(@PathVariable String msisdn) {
        return hatlar.ozet(msisdn);
    }

    @PostMapping("/hatlar/{msisdn}/kullanim")
    public HatServisi.HatOzeti kullanim(@PathVariable String msisdn, @Valid @RequestBody KullanimIstegi istek) {
        return hatlar.kullanimEkle(msisdn, istek.mb());
    }

    @GetMapping("/hatlar/{msisdn}/fatura-kalemleri")
    public List<HatServisi.FaturaKalemiOzeti> faturaKalemleri(@PathVariable String msisdn) {
        return hatlar.faturaKalemleri(msisdn);
    }

    @GetMapping("/tarifeler")
    public List<HatServisi.TarifeOzeti> tarifeler() {
        return hatlar.satistakiTarifeler();
    }
}
