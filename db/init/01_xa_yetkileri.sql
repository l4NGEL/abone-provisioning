-- gvenzl/oracle-free ilk acilista SYSDBA olarak calistirir.
-- Atomikos'un XA kurtarma taramasi (recover) icin gereken yetkiler.
ALTER SESSION SET CONTAINER = FREEPDB1;
GRANT SELECT ON SYS.DBA_PENDING_TRANSACTIONS TO ABONE;
GRANT FORCE ANY TRANSACTION TO ABONE;
GRANT EXECUTE ON SYS.DBMS_XA TO ABONE;
