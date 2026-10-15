package dev.abone.provisioning.rest;

import dev.abone.provisioning.domain.Kanal;
import dev.abone.provisioning.domain.SiparisTipi;
import dev.abone.provisioning.servis.SiparisGorunumu;
import dev.abone.provisioning.servis.SiparisKomutu;
import dev.abone.provisioning.servis.SiparisServisi;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/siparisler")
@Validated
public class SiparisController {

    public record SiparisIstegi(
            @NotBlank @Size(max = 64) String istemciRef,
            @NotNull SiparisTipi tip,
            @NotNull @Pattern(regexp = "905\\d{9}", message = "905XXXXXXXXX biciminde olmali") String msisdn,
            @Size(max = 20) String hedefTarifeKodu) {
    }

    private final SiparisServisi siparisler;

    public SiparisController(SiparisServisi siparisler) {
        this.siparisler = siparisler;
    }

    /** Yeni siparis 202 Accepted; ayni istemciRef ile tekrar 200 ve mevcut siparis. */
    @PostMapping
    public ResponseEntity<SiparisGorunumu> olustur(@Valid @RequestBody SiparisIstegi istek) {
        SiparisServisi.Sonuc sonuc = siparisler.olustur(
                new SiparisKomutu(istek.istemciRef(), istek.tip(), istek.msisdn(), istek.hedefTarifeKodu()), Kanal.REST);
        if (sonuc.tekrarIstek()) {
            return ResponseEntity.ok(sonuc.siparis());
        }
        return ResponseEntity.accepted()
                .location(URI.create("/api/siparisler/" + sonuc.siparis().id()))
                .body(sonuc.siparis());
    }

    @GetMapping("/{id}")
    public SiparisGorunumu bul(@PathVariable long id) {
        return siparisler.bul(id);
    }

    @GetMapping
    public List<SiparisGorunumu> son(@RequestParam(defaultValue = "50") @Min(1) @Max(500) int adet) {
        return siparisler.sonSiparisler(adet);
    }
}
