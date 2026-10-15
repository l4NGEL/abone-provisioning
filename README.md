# abone-provisioning

Mobil hat **aktivasyonu** ve **tarife değişikliği** siparişlerini alan, Oracle'da kaydedip JMS kuyruğu
üzerinden asenkron işleyen bir provisioning servisi. Ücret ve kota hesabı Oracle'daki bir PL/SQL
paketinde; sipariş kaydı ile kuyruk mesajı tek bir dağıtık (XA) işlemde. Aynı işlemler REST, SOAP ve
bir JSF operatör ekranından yapılabiliyor.

Spring Boot 3.5 · Java 17 · JPA/Hibernate · Oracle 23ai Free · PL/SQL · ActiveMQ Artemis (JMS) ·
Atomikos (JTA/XA) · Spring-WS (SOAP, contract-first) · JSF 4 + PrimeFaces (JoinFaces) · Flyway ·
JUnit 5 + Testcontainers · k6 · Docker Compose · Jenkins · Kubernetes/OpenShift

Tüm veriler sentetiktir (uydurma ad ve TCKN'ler).

## Akış

```
 REST  /api/siparisler ─┐
 SOAP  /ws (WSDL)      ─┼─► SiparisServisi ──► [ JTA/XA işlemi ]
 JSF   /operator.xhtml ─┘                        ├─ INSERT SIPARIS (durum=ALINDI)      → Oracle
                                                 └─ mesaj {siparisId}                    → Artemis
                                                       2PC: ikisi birlikte commit / rollback
                                                                 │
                         SiparisDinleyici (JMS, 4-8 tüketici) ◄──┘
                                 │   [ JTA/XA işlemi: mesaj tüketimi + DB güncellemesi ]
                                 ▼
                    PKG_FATURA.HAT_AKTIVE_ET / TARIFE_DEGISTIR   (SELECT ... FOR UPDATE)
                                 │
                   SIPARIS → TAMAMLANDI (kıst ücret, devreden kota)
                          veya REDDEDILDI (ORA-20001..20004 iş kuralı mesajı)
```

İstemci siparişi gönderip `202 Accepted` ve sipariş numarası alır, sonucu sorgulayarak öğrenir.
Çağıran sistem her istekte bir `istemciRef` gönderir; ağ kesintisi sonrası aynı istek tekrar gelirse
yeni sipariş açılmaz, mevcut sipariş `200` ile döner. Aynı `istemciRef` farklı bir istek için
kullanılırsa `409`.

## XA işlemi neyi koruyor

Hibernate `INSERT`'i commit anına kadar erteler. Yani aynı `istemciRef` ile eşzamanlı gelen
isteklerde tekillik ihlali (ORA-00001) **mesaj kuyruğa gönderildikten sonra** ortaya çıkar. XA bu
noktada kaybeden isteklerin mesajını da geri alır.

`XaIslemTesti` bunu 8 eşzamanlı istekle sınıyor: 7'si ön kontrolü geçip commit'te geri alınıyor,
veritabanında tek satır, işlenen tek sipariş, sahipsiz mesaj sıfır. Testin XA'yı gerçekten sınadığını
görmek için JMS bağlantı fabrikasını geçici olarak XA'sız `ActiveMQConnectionFactory` ile değiştirip
aynı testi koştum; test kırmızıya döndü ve **8** sahipsiz mesaj oluştu. Beklenen 7'nin yanında
kazananın mesajı da DB commit'inden önce tüketildiği için sipariş `ALINDI`'da takılı kaldı. Yani XA
burada iki ayrı hatayı kapatıyor: hayalet mesajlar ve "commit'ten önce okunan" mesajlar.

XA'nın kendi içinde de dar bir pencere var: 2PC'nin commit fazında kaynaklar sırayla commit edilir ve
Artemis Oracle'dan önce commit ederse dinleyici satırı henüz göremeyebilir. Bu yüzden bulunamayan
sipariş hemen "sahipsiz" sayılmıyor; işlem geri alınıp mesaj yeniden teslim ediliyor
(`JMSXDeliveryCount` < 5). Test edildi: karşılığı olmayan bir mesaj tam 4 kez geri çevrilip 5.
teslimde sahipsiz sayılıyor. Yük testlerinde bu durum hiç oluşmadı (`provisioning_mesaj_erken_teslim_total 0`).

Atomikos işlem günlüğü kalıcı bir dizinde tutuluyor (compose'da volume, K8s'te pod başına PVC).
Uygulama prepare ile commit arasında çökerse yeniden açılışta yarım kalan 2PC'ler bu günlükten
tamamlanıyor. Oracle tarafında bunun için gereken `DBA_PENDING_TRANSACTIONS` / `FORCE ANY TRANSACTION`
yetkileri [db/init/01_xa_yetkileri.sql](db/init/01_xa_yetkileri.sql) ile veriliyor. Bu kurtarma
senaryosunu (sürecin 2PC ortasında öldürülmesi) **elle denemedim**.

## Ücret ve kota modeli (PL/SQL)

[V2__pkg_fatura.sql](src/main/resources/db/migration/V2__pkg_fatura.sql) — `PKG_FATURA`

- Aylık ücret dönem başında peşin kesilir; ilk ay aktivasyonda **kıst** (kalan gün oranı) olarak.
  `kalan_gun_orani = (ay_gun - gun + 1) / ay_gun` → 11 Ekim için 21/31.
- Tarife değişikliğinde eski tarifenin kalan günleri iade edilir, yeni tarifenin kalan günleri kesilir:
  `net = (yeni - eski) × oran`. GNC10 (250 ₺) → PLT20 (450 ₺), 11 Ekim: −169,35 + 304,84 = **135,49 ₺**.
  Düşük tarifeye geçişte tutar negatif (mahsup).
- Eski tarifeden kullanılmayan kota, yeni tarifenin aylık kotasını aşmayacak şekilde devreder.
- Hat satırı `SELECT ... FOR UPDATE` ile kilitlenir; aynı hatta eşzamanlı iki sipariş sırayla işlenir.
  Java tarafındaki ön kontroller sadece hızlı geri bildirim içindir; asıl karar kilit altında PL/SQL'de.
- İş kuralı ihlalleri `RAISE_APPLICATION_ERROR(-20001..-20004)`. Java tarafı bu aralığı "reddedildi",
  diğer Oracle hatalarını teknik hata (rollback + yeniden teslim) olarak yorumluyor.

PL/SQL çağrıları `SimpleJdbcCall` ile yapılıyor, JPA ile değil: `EntityManager` üzerinden gelen bir
SQL hatası Hibernate'in JTA işlemini rollback-only işaretliyor ve siparişi `REDDEDILDI` olarak
kaydetmek mümkün olmuyordu.

Kapsam dışı: dönem sonu kota sıfırlama ve aylık faturalama koşusu (batch).

## Çalıştırma

```bash
docker compose up -d --build --wait
```

Oracle Free (slim-faststart), Artemis ve uygulama imajı ayağa kalkar; Flyway şemayı, PL/SQL paketini
ve örnek veriyi yükler. Temiz bir makinede imaj derlemesi dahil 2 dk 40 sn sürdü.

| Adres | |
|---|---|
| http://localhost:8080/ | JSF operatör ekranı |
| http://localhost:8080/ws/provisioning.wsdl | WSDL (XSD'den üretilir) |
| http://localhost:8080/actuator/prometheus | Metrikler |
| http://localhost:8161/ | Artemis konsolu (artemis/artemis) |

Denemek için hatlar: `905300000002`, `905300000003` beklemede; `905300000001` aktif (GNC10);
`905300000004` satıştan kalkmış ESKI4 tarifesinde.

```bash
curl -X POST localhost:8080/api/siparisler -H 'Content-Type: application/json' \
  -d '{"istemciRef":"deneme-1","tip":"TARIFE_DEGISIKLIGI","msisdn":"905300000001","hedefTarifeKodu":"PLT20"}'
```

### REST

| | |
|---|---|
| `POST /api/siparisler` | sipariş oluştur → `202` (yeni) / `200` (aynı istemciRef) / `409` / `422` |
| `GET /api/siparisler/{id}` | sipariş durumu |
| `GET /api/siparisler?adet=50` | son siparişler |
| `GET /api/hatlar/{msisdn}` | hat özeti + kalan kota (`PKG_FATURA.KALAN_KOTA`) |
| `POST /api/hatlar/{msisdn}/kullanim` | şebeke kullanım kaydı taklidi `{"mb": 500}` |
| `GET /api/hatlar/{msisdn}/fatura-kalemleri` | PL/SQL'in yazdığı kalemler |
| `GET /api/tarifeler` | satıştaki tarifeler |

Hatalar RFC 9457 `application/problem+json`.

### SOAP

Contract-first: [provisioning.xsd](src/main/resources/xsd/provisioning.xsd) tek kaynak; JAXB
sınıfları derlemede üretilir, WSDL çalışma zamanında XSD'den türetilir. İşlemler `SiparisOlustur`,
`SiparisSorgula`, `HatSorgula`. İş hataları `Client`, diğerleri `Server` fault.

### Operatör ekranı (JSF)

Tek sayfa: hat sorgula → hatta uygulanabilen işlemi gör (beklemedeyse aktivasyon, aktifse tarife
seçimi) → onay diyaloğu → son siparişler tablosu.

- Açılışta odak MSISDN alanında, Enter sorgular; alan sayısal klavye açar ve biçimi doğrular.
- Hatta yapılamayacak işlem hiç gösterilmez; hedef tarife listesinde mevcut tarife yoktur.
- Her form gösterimi bir `istemciRef` taşır: çift tıklama veya ağ kesintisi sonrası tekrar gönderim
  ikinci sipariş açmaz, "bu istek zaten alınmıştı" der.
- Bekleyen sipariş varken tablo 2 sn'de bir yenilenir, kalmayınca yoklama durur ve hat bilgisi
  (durum, kota, fatura kalemleri) kendiliğinden tazelenir.

Jakarta EL 5.0 Java record'larını bean olarak görmüyor; servis katmanı record döndürdüğü için küçük
bir [RecordElCozucu](src/main/java/dev/abone/provisioning/web/RecordElCozucu.java) eklendi.

## Testler

```bash
./mvnw test
```

27 test; Oracle 23ai Free ve Artemis'i Testcontainers ile gerçek kapsayıcı olarak kaldırıyor. Tüm
paket bu makinede ~1 dk sürüyor (çoğu kapsayıcı açılışı); Docker gerekir.

| Sınıf | Ne doğruluyor |
|---|---|
| `PkgFaturaTesti` (10) | PL/SQL paketinin `VALID` derlendiği; kıst ücret/kota/devir hesapları; artık yıl; −2000x hata kodları; reddedilen çağrının hat satırına dokunmadığı |
| `SiparisAkisTesti` (7) | REST → kuyruk → PL/SQL → `TAMAMLANDI`; idempotent tekrar; 409; ön kontrolü geçip kilit altında reddedilen yarış; 400/404/422; kullanım → kalan kota |
| `XaIslemTesti` (2) | eşzamanlı aynı istemciRef'te tek satır + sıfır sahipsiz mesaj; karşılıksız mesajın 5 teslimden sonra sahipsiz sayılması |
| `SoapUcNoktaTesti` (3) | WSDL içeriği; JAXB istemcisiyle tarife değişikliği ve sorgu; Client fault |
| `OperatorEkraniTesti` (2) | JSF sayfasının render edilmesi, kök adres yönlendirmesi |
| `OracleHataCeviriciTesti` (3) | ORA mesaj ayrıştırma (kapsayıcısız birim testi) |

`PkgFaturaTesti.paketGecerliDerlenmis` gerçek bir hatadan doğdu: Flyway, derleme hatası olan PL/SQL
paket gövdesini yalnızca uyarıyla (ORA-17110) geçirip migrasyonu başarılı saydı; paket `INVALID`
kaldı. Artık `USER_OBJECTS`'te geçersiz nesne varsa test kırılıyor.

JSF etkileşimi (sorgula → aktive et → onay → yoklamayla tamamlanma) tarayıcıda elle denendi;
otomatik bir UI testi (Selenium vb.) yok.

## Performans (k6)

[perf/k6-siparis.js](perf/k6-siparis.js): hat sorgula → tarife değişikliği siparişi → `TAMAMLANDI`
olana kadar 50 ms aralıkla sorgula. Her 10 iterasyonda bir aynı istemciRef ile tekrar gönderip aynı
siparişin döndüğünü kontrol ediyor. Her sanal kullanıcı yük havuzundaki 200 hattın kendine ait bir
dilimini kullanıyor; böylece `REDDEDILDI` sonucu gerçek bir hata anlamına geliyor.

```bash
docker run --rm --network abone-provisioning_default -e BASE_URL=http://app:8080 \
  -e SENARYO=yuk -e VU_SAYISI=20 -v "$PWD/perf:/perf" grafana/k6:0.57.0 run /perf/k6-siparis.js
```

Ortam: tek dizüstü bilgisayar (i9-9880H, 16 mantıksal çekirdek), Docker Desktop/WSL2. Oracle,
Artemis, uygulama ve k6 aynı makinede. Ölçüm süresi 80 sn (15 sn artış + 60 sn sabit + 5 sn iniş).

| | 1 VU (smoke, 20 iter.) | 20 VU | 40 VU |
|---|---|---|---|
| Tamamlanan sipariş | 20 / 20 | 1557 / 1557 | 1331 / 1334 (10 sn içinde) |
| Verim | — | 19,5 sipariş/sn | 16,7 sipariş/sn |
| `POST /api/siparisler` p95 | 85 ms | 449 ms | 861 ms |
| Uçtan uca (POST → TAMAMLANDI) p50 / p95 | 150 / 184 ms | 753 / 976 ms | 1,6 / 3,1 sn |
| HTTP hata oranı | 0 | 0 | 0 |
| Sahipsiz / erken teslim mesaj | 0 / 0 | 0 / 0 | 0 / 0 |

40 VU'da k6 eşikleri (POST p95 < 500 ms, uçtan uca p95 < 2 sn) aşıldı ve verim 20 VU'nun altına
düştü; sistem bu ortamda ~20 sipariş/sn civarında doyuyor. 10 sn'yi aşan 3 sipariş de sonradan
tamamlandı (en uzunu 18,5 sn); toplam 2911 siparişin hiçbiri takılmadı veya reddedilmedi.

Doygunlukta CPU dolmuyordu (40 VU sırasında uygulama ~%40-50, Oracle ~%25-30; Artemis kısa bir an
%150). Tahminim darboğazın her 2PC'deki disk senkronizasyonu (Artemis journal, Oracle redo, Atomikos
günlüğü) ve bunun WSL2 sanal diskindeki gecikmesi olduğu; bunu profil çıkararak **doğrulamadım**.
Gerçek sunucu diskinde ve ayrı makinelerde sayılar farklı olacaktır.

## CI/CD ve Kubernetes

[Jenkinsfile](Jenkinsfile): derleme → Testcontainers testleri (JUnit raporu) → imaj → compose + k6
smoke → (main) registry'ye push → (main) `kubectl apply -k` ve `rollout status`. Jenkinsfile'ı
**gerçek bir Jenkins üzerinde çalıştırmadım**; içindeki adımları (mvnw, docker build, compose + k6
smoke) yerelde tek tek koştum.

[k8s/](k8s/) kustomize ile: namespace, ConfigMap, secretGenerator, Oracle ve Artemis StatefulSet'leri,
uygulama StatefulSet'i, OpenShift için ayrı bir Route. `kubectl kustomize k8s` çıktısı kubeconform
(`-strict`) ile 11/11 kaynak geçerli; **bir kümeye dağıtılmadı**.

Uygulama Deployment değil StatefulSet: her pod Atomikos işlem günlüğünü kendi PVC'sinde tutuyor ve
pod adını `tm_unique_name` olarak kullanıyor. Pod yeniden oluştuğunda aynı adla aynı günlüğü bulup
yarım kalan 2PC'leri tamamlayabiliyor. İmaj OpenShift'in rastgele UID politikasına uygun (yazılabilir
dizinler GID 0'a açık, `USER 10001`).

## Bilinen sınırlar

- JTA, uygulama sunucusu yerine gömülü Atomikos ile; EJB kullanılmadı (Spring bean'leri).
- Tek Artemis broker'ı; HA (canlı/yedek) çifti yok.
- Flyway 11, Oracle 23 için "test edilmedi" uyarısı veriyor; migrasyonlar sorunsuz çalıştı.
- Kimlik doğrulama/yetkilendirme yok.
- kustomization'daki şifreler yalnızca yerel deneme içindir; gerçek ortamda Vault/SealedSecret.
- JSF ekranı tek operatör senaryosu için; sayfalama ve yetki kontrolü yok.

## Proje yapısı

```
src/main/java/dev/abone/provisioning/
  config/    JTA (Atomikos: Oracle XA + Artemis XA), saat, yönlendirme
  domain/    JPA varlıkları ve Spring Data depoları
  servis/    sipariş kaydı (XA), PL/SQL erişimi, sipariş işleme, hat sorguları
  mesaj/     JMS dinleyici ve mesaj biçimi
  rest/      REST denetleyicileri, problem+json
  soap/      Spring-WS uç noktası, WSDL, fault eşleme
  web/       JSF operatör bean'i, record EL çözücü
src/main/resources/
  db/migration/        V1 şema, V2 PL/SQL paketi, V3 örnek veri
  xsd/                 SOAP sözleşmesi
  META-INF/resources/  operator.xhtml
db/init/   Oracle ilk açılış yetkileri (XA kurtarma)
perf/      k6 betiği
k8s/       kustomize manifestleri
```
