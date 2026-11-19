ALTER TABLE app_user
ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT 'USER=买家，MERCHANT=商家，ADMIN=平台运营';

UPDATE app_user
SET role = 'ADMIN'
WHERE
    username = 'demo_merchant';