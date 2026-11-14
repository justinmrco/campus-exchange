-- Existing installations: back up first. Select the application database explicitly.
-- Apply once; the guard rejects a repeated or conflicting migration without altering data.
DELIMITER //
CREATE PROCEDURE ce_add_listing_images()
BEGIN
 IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='listings' AND column_name='image_path') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='image_path already exists; inspect schema instead of rerunning';
 END IF;
 ALTER TABLE listings ADD COLUMN image_path VARCHAR(255) NULL;
END//
DELIMITER ;
CALL ce_add_listing_images();
DROP PROCEDURE ce_add_listing_images;
