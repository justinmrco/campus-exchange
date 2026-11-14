-- Existing installations ONLY. Back up first; inspect SHOW CREATE TABLE for all four tables.
-- Never run after schema.sql. DDL commits independently: restore/review manually if an ALTER fails.
-- MySQL client: select the existing database, then SOURCE this file. Do not use --force.
-- The guard keeps all ALTERs inside the call, so duplicate disputes prevent every alteration.
DELIMITER //
CREATE PROCEDURE ce_upgrade_20261114()
BEGIN
 IF EXISTS (SELECT 1 FROM disputes GROUP BY ledger_id HAVING COUNT(*) > 1) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Duplicate disputes: review history before migrating; nothing altered';
 END IF;
 IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='users' AND column_name='active')
 OR EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='handover_ledger' AND column_name='version') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Migration already or partially applied: inspect schema before proceeding';
 END IF;
 ALTER TABLE users ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE, MODIFY password_hash VARCHAR(255) NOT NULL;
 ALTER TABLE handover_ledger ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
 ALTER TABLE disputes ADD CONSTRAINT uq_dispute_ledger UNIQUE(ledger_id);
END//
DELIMITER ;
CALL ce_upgrade_20261114();
DROP PROCEDURE ce_upgrade_20261114;
