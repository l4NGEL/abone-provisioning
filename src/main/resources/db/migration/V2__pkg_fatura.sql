-- Kota ve kist (gun bazli oransal) ucret hesaplari.
-- Is kurali ihlalleri ORA-20001..20099 araliginda firlatilir; Java tarafi bu araligi
-- "reddedildi" olarak, digerlerini teknik hata (yeniden deneme) olarak yorumlar.

CREATE OR REPLACE PACKAGE PKG_FATURA AS
    HATA_AYNI_TARIFE     CONSTANT PLS_INTEGER := -20001;
    HATA_HAT_DURUMU      CONSTANT PLS_INTEGER := -20002;
    HATA_TARIFE_PASIF    CONSTANT PLS_INTEGER := -20003;
    HATA_HAT_YOK         CONSTANT PLS_INTEGER := -20004;

    -- Donem kotasindan, kota baslangicindan bu yana yapilan kullanim dusulur (alt sinir 0).
    FUNCTION KALAN_KOTA(p_hat_id IN NUMBER) RETURN NUMBER;

    -- p_tarih gunu dahil ay sonuna kadar kalan gun orani: 31 gunluk ayin 11'i -> 21/31
    FUNCTION KALAN_GUN_ORANI(p_tarih IN DATE) RETURN NUMBER;

    PROCEDURE HAT_AKTIVE_ET(
        p_hat_id      IN  NUMBER,
        p_zaman       IN  TIMESTAMP,
        o_kist_ucret  OUT NUMBER,
        o_kota_mb     OUT NUMBER);

    -- Aylik ucret donem basinda (ilk ay: aktivasyonda kist olarak) pesin kesilir. Degisiklikte
    -- eski tarifenin kalan gunleri iade edilir, yeni tarifenin kalan gunleri kesilir:
    -- net kist = (yeni - eski) * kalan_gun_orani (dusuk tarifeye geciste negatif = mahsup).
    -- Eski tarifeden kullanilmayan kota, yeni tarifenin aylik kotasini asmayacak sekilde devreder.
    PROCEDURE TARIFE_DEGISTIR(
        p_hat_id          IN  NUMBER,
        p_yeni_tarife_id  IN  NUMBER,
        p_zaman           IN  TIMESTAMP,
        o_kist_ucret      OUT NUMBER,
        o_devreden_mb     OUT NUMBER);

    PROCEDURE KULLANIM_EKLE(
        p_hat_id  IN NUMBER,
        p_mb      IN NUMBER,
        p_zaman   IN TIMESTAMP);
END PKG_FATURA;
/

CREATE OR REPLACE PACKAGE BODY PKG_FATURA AS

    FUNCTION DONEM_OF(p_tarih IN DATE) RETURN CHAR IS
    BEGIN
        RETURN TO_CHAR(p_tarih, 'YYYY-MM');
    END;

    FUNCTION KALAN_GUN_ORANI(p_tarih IN DATE) RETURN NUMBER IS
        v_ay_gun  NUMBER := EXTRACT(DAY FROM LAST_DAY(p_tarih));
        v_gun     NUMBER := EXTRACT(DAY FROM p_tarih);
    BEGIN
        RETURN (v_ay_gun - v_gun + 1) / v_ay_gun;
    END;

    FUNCTION KALAN_KOTA(p_hat_id IN NUMBER) RETURN NUMBER IS
        v_kota      HAT.KOTA_MB%TYPE;
        v_baslangic HAT.KOTA_BASLANGIC%TYPE;
        v_kullanim  NUMBER;
    BEGIN
        SELECT KOTA_MB, KOTA_BASLANGIC INTO v_kota, v_baslangic FROM HAT WHERE ID = p_hat_id;
        IF v_baslangic IS NULL THEN
            RETURN 0;
        END IF;
        SELECT NVL(SUM(DATA_MB), 0) INTO v_kullanim
          FROM KULLANIM
         WHERE HAT_ID = p_hat_id AND KAYIT_ZAMANI >= v_baslangic;
        RETURN GREATEST(v_kota - v_kullanim, 0);
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(HATA_HAT_YOK, 'Hat bulunamadi: ' || p_hat_id);
    END;

    -- Zaman uygulamadan gelir: DB sunucusunun saat dilimi (konteynerde UTC) kullanilmaz
    PROCEDURE KALEM_EKLE(p_hat_id NUMBER, p_zaman TIMESTAMP, p_aciklama VARCHAR2, p_tutar NUMBER) IS
        -- paket ici (spec'te olmayan) fonksiyon SQL icinden cagrilamaz, once degiskene al
        v_donem FATURA_KALEMI.DONEM%TYPE := DONEM_OF(CAST(p_zaman AS DATE));
    BEGIN
        INSERT INTO FATURA_KALEMI (ID, HAT_ID, DONEM, ACIKLAMA, TUTAR, OLUSTURMA)
        VALUES (SEQ_FATURA_KALEMI.NEXTVAL, p_hat_id, v_donem, p_aciklama, p_tutar, p_zaman);
    END;

    PROCEDURE HAT_AKTIVE_ET(
        p_hat_id      IN  NUMBER,
        p_zaman       IN  TIMESTAMP,
        o_kist_ucret  OUT NUMBER,
        o_kota_mb     OUT NUMBER)
    IS
        v_durum   HAT.DURUM%TYPE;
        v_tarife  TARIFE%ROWTYPE;
        v_tarih   DATE := TRUNC(CAST(p_zaman AS DATE));
        v_oran    NUMBER;
    BEGIN
        -- ayni hat icin es zamanli siparisleri sirala
        SELECT H.DURUM INTO v_durum FROM HAT H WHERE H.ID = p_hat_id FOR UPDATE;
        IF v_durum <> 'BEKLEMEDE' THEN
            RAISE_APPLICATION_ERROR(HATA_HAT_DURUMU,
                'Sadece BEKLEMEDE durumundaki hat aktive edilebilir (mevcut: ' || v_durum || ')');
        END IF;

        SELECT T.* INTO v_tarife FROM TARIFE T JOIN HAT H ON H.TARIFE_ID = T.ID WHERE H.ID = p_hat_id;
        IF v_tarife.AKTIF <> 'E' THEN
            RAISE_APPLICATION_ERROR(HATA_TARIFE_PASIF, 'Tarife satista degil: ' || v_tarife.KOD);
        END IF;

        v_oran       := KALAN_GUN_ORANI(v_tarih);
        o_kist_ucret := ROUND(v_tarife.AYLIK_UCRET * v_oran, 2);
        o_kota_mb    := ROUND(v_tarife.DATA_MB * v_oran, 2);

        UPDATE HAT
           SET DURUM = 'AKTIF',
               AKTIVASYON_TARIHI = v_tarih,
               KOTA_MB = o_kota_mb,
               KOTA_BASLANGIC = p_zaman,
               VERSIYON = VERSIYON + 1
         WHERE ID = p_hat_id;

        KALEM_EKLE(p_hat_id, p_zaman, v_tarife.KOD || ' ilk ay kist ucret', o_kist_ucret);
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(HATA_HAT_YOK, 'Hat bulunamadi: ' || p_hat_id);
    END;

    PROCEDURE TARIFE_DEGISTIR(
        p_hat_id          IN  NUMBER,
        p_yeni_tarife_id  IN  NUMBER,
        p_zaman           IN  TIMESTAMP,
        o_kist_ucret      OUT NUMBER,
        o_devreden_mb     OUT NUMBER)
    IS
        v_durum     HAT.DURUM%TYPE;
        v_eski_id   HAT.TARIFE_ID%TYPE;
        v_eski      TARIFE%ROWTYPE;
        v_yeni      TARIFE%ROWTYPE;
        v_tarih     DATE := TRUNC(CAST(p_zaman AS DATE));
        v_oran      NUMBER;
        v_eski_iade NUMBER;
        v_yeni_pay  NUMBER;
    BEGIN
        SELECT DURUM, TARIFE_ID INTO v_durum, v_eski_id FROM HAT WHERE ID = p_hat_id FOR UPDATE;
        IF v_durum <> 'AKTIF' THEN
            RAISE_APPLICATION_ERROR(HATA_HAT_DURUMU,
                'Tarife degisikligi icin hat AKTIF olmali (mevcut: ' || v_durum || ')');
        END IF;
        IF v_eski_id = p_yeni_tarife_id THEN
            RAISE_APPLICATION_ERROR(HATA_AYNI_TARIFE, 'Hat zaten bu tarifede');
        END IF;

        SELECT * INTO v_eski FROM TARIFE WHERE ID = v_eski_id;
        BEGIN
            SELECT * INTO v_yeni FROM TARIFE WHERE ID = p_yeni_tarife_id;
        EXCEPTION
            WHEN NO_DATA_FOUND THEN
                RAISE_APPLICATION_ERROR(HATA_TARIFE_PASIF, 'Tarife bulunamadi: ' || p_yeni_tarife_id);
        END;
        IF v_yeni.AKTIF <> 'E' THEN
            RAISE_APPLICATION_ERROR(HATA_TARIFE_PASIF, 'Tarife satista degil: ' || v_yeni.KOD);
        END IF;

        v_oran     := KALAN_GUN_ORANI(v_tarih);
        v_eski_iade := -ROUND(v_eski.AYLIK_UCRET * v_oran, 2);
        v_yeni_pay  := ROUND(v_yeni.AYLIK_UCRET * v_oran, 2);
        o_kist_ucret  := v_yeni_pay + v_eski_iade;
        o_devreden_mb := LEAST(KALAN_KOTA(p_hat_id), v_yeni.DATA_MB);

        UPDATE HAT
           SET TARIFE_ID = p_yeni_tarife_id,
               KOTA_MB = ROUND(v_yeni.DATA_MB * v_oran, 2) + o_devreden_mb,
               KOTA_BASLANGIC = p_zaman,
               VERSIYON = VERSIYON + 1
         WHERE ID = p_hat_id;

        KALEM_EKLE(p_hat_id, p_zaman, v_eski.KOD || ' kalan gun iadesi (' || TO_CHAR(v_tarih, 'DD.MM') || ' itibariyla)', v_eski_iade);
        KALEM_EKLE(p_hat_id, p_zaman, v_yeni.KOD || ' kist ucret (' || TO_CHAR(v_tarih, 'DD.MM') || ' itibariyla)', v_yeni_pay);
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(HATA_HAT_YOK, 'Hat bulunamadi: ' || p_hat_id);
    END;

    PROCEDURE KULLANIM_EKLE(p_hat_id IN NUMBER, p_mb IN NUMBER, p_zaman IN TIMESTAMP) IS
        v_durum HAT.DURUM%TYPE;
    BEGIN
        SELECT DURUM INTO v_durum FROM HAT WHERE ID = p_hat_id;
        IF v_durum <> 'AKTIF' THEN
            RAISE_APPLICATION_ERROR(HATA_HAT_DURUMU, 'Kullanim sadece AKTIF hatta yazilabilir');
        END IF;
        INSERT INTO KULLANIM (ID, HAT_ID, DATA_MB, KAYIT_ZAMANI)
        VALUES (SEQ_KULLANIM.NEXTVAL, p_hat_id, p_mb, p_zaman);
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(HATA_HAT_YOK, 'Hat bulunamadi: ' || p_hat_id);
    END;
END PKG_FATURA;
/
