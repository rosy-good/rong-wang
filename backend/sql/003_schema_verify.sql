-- 在 rongwang_dev 中执行；只读，不修改任何表或账号。
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'rongwang_dev' AND LEFT(table_name, 3) = 'rw_'
ORDER BY table_name;

SELECT COUNT(*) AS table_count
FROM information_schema.tables
WHERE table_schema = 'rongwang_dev' AND LEFT(table_name, 3) = 'rw_';

SELECT COUNT(*) AS foreign_key_count
FROM information_schema.referential_constraints
WHERE constraint_schema = 'rongwang_dev';

SELECT id, username, role, status FROM rongwang_dev.rw_user ORDER BY id;
