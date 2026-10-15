package dev.abone.provisioning.servis;

import dev.abone.provisioning.domain.SiparisTipi;

/**
 * @param istemciRef cagiran sistemin tekil istek anahtari; ag hatasi sonrasi tekrar gonderimde
 *                   ayni siparis doner (idempotent)
 */
public record SiparisKomutu(String istemciRef, SiparisTipi tip, String msisdn, String hedefTarifeKodu) {
}
