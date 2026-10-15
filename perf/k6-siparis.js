// Tarife degisikligi siparisi: POST -> kuyruk -> PL/SQL -> TAMAMLANDI, uctan uca.
//
//   SENARYO=smoke  1 VU, 20 iterasyon; CI'da "ayakta mi, akis calisiyor mu" kontrolu
//   SENARYO=yuk    0 -> VU_SAYISI VU, 60 sn sabit yuk
//
// Her VU yuk havuzundaki (905390000000..199) hatlarin kendine ait bir dilimini kullanir;
// ayni hatta es zamanli iki siparis olusmaz, boylece REDDEDILDI sonuclari gercek hata demektir.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SENARYO = __ENV.SENARYO || 'smoke';
const VU_SAYISI = parseInt(__ENV.VU_SAYISI || '20', 10);
const HAVUZ = 200;

const uctanUca = new Trend('siparis_uctan_uca_ms', true);
const tamamlandi = new Rate('siparis_tamamlandi');
const tekrarDogru = new Rate('tekrar_istek_ayni_siparis');
const zamanAsimi = new Counter('siparis_zaman_asimi');

const senaryolar = {
  smoke: {
    executor: 'per-vu-iterations',
    vus: 1,
    iterations: 20,
    maxDuration: '2m',
  },
  yuk: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '15s', target: VU_SAYISI },
      { duration: '60s', target: VU_SAYISI },
      { duration: '5s', target: 0 },
    ],
    gracefulRampDown: '15s',
  },
};

export const options = {
  scenarios: { [SENARYO]: senaryolar[SENARYO] },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{islem:siparis_olustur}': ['p(95)<500'],
    siparis_tamamlandi: ['rate>0.99'],
    siparis_uctan_uca_ms: ['p(95)<2000'],
    tekrar_istek_ayni_siparis: ['rate==1'],
  },
};

const JSON_BASLIK = { headers: { 'Content-Type': 'application/json' } };

function hatSec() {
  // VU basina sabit bir dilim; iterasyonla dilim icinde dolasir
  const dilim = Math.max(1, Math.floor(HAVUZ / Math.max(VU_SAYISI, 1)));
  const indeks = ((__VU - 1) * dilim + (__ITER % dilim)) % HAVUZ;
  return '90539' + String(indeks).padStart(7, '0');
}

export default function () {
  const msisdn = hatSec();

  const hat = http.get(`${BASE_URL}/api/hatlar/${msisdn}`, { tags: { islem: 'hat_sorgula' } });
  if (!check(hat, { 'hat 200': (r) => r.status === 200 })) {
    return;
  }
  const hedef = hat.json('tarifeKodu') === 'GNC10' ? 'PLT20' : 'GNC10';
  const govde = JSON.stringify({
    istemciRef: `k6-${SENARYO}-${__VU}-${__ITER}-${Date.now()}`,
    tip: 'TARIFE_DEGISIKLIGI',
    msisdn,
    hedefTarifeKodu: hedef,
  });

  const baslangic = Date.now();
  const yanit = http.post(`${BASE_URL}/api/siparisler`, govde, { ...JSON_BASLIK, tags: { islem: 'siparis_olustur' } });
  if (!check(yanit, { 'siparis 202': (r) => r.status === 202 })) {
    tamamlandi.add(false);
    return;
  }
  const id = yanit.json('id');

  // Ayni istemciRef ile tekrar: yeni siparis olusmamali (her 10 iterasyonda bir)
  if (__ITER % 10 === 0) {
    const tekrar = http.post(`${BASE_URL}/api/siparisler`, govde, { ...JSON_BASLIK, tags: { islem: 'siparis_tekrar' } });
    tekrarDogru.add(tekrar.status === 200 && tekrar.json('id') === id);
  }

  let durum = 'ALINDI';
  while (Date.now() - baslangic < 10000) {
    const s = http.get(`${BASE_URL}/api/siparisler/${id}`, { tags: { islem: 'siparis_sorgula' } });
    durum = s.json('durum');
    if (durum !== 'ALINDI') {
      break;
    }
    sleep(0.05);
  }
  if (durum === 'ALINDI') {
    zamanAsimi.add(1);
  } else {
    uctanUca.add(Date.now() - baslangic);
  }
  tamamlandi.add(durum === 'TAMAMLANDI');
}
