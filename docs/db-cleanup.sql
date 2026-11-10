-- 기존 DB 수동 정리용. 전체 파일을 한 번에 실행하지 말고 구간별로 확인한다.
-- 1. 사전 확인: 대상 DB 선택 후 실행한다.
SELECT DATABASE();

SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND ((LOWER(TABLE_NAME) = 'users' AND UPPER(COLUMN_NAME) = 'BIRTH_DATE')
       OR LOWER(TABLE_NAME) IN ('user_sanctions', 'user_login_security', 'tax_process_logs'))
ORDER BY TABLE_NAME, ORDINAL_POSITION;

-- 삭제 대상 테이블을 다른 테이블이 참조하는지 확인한다.
SELECT TABLE_NAME, CONSTRAINT_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE REFERENCED_TABLE_SCHEMA = DATABASE()
  AND LOWER(REFERENCED_TABLE_NAME) IN ('user_sanctions', 'user_login_security', 'tax_process_logs');

-- 뷰·트리거·이벤트·프로시저도 SHOW CREATE로 정의를 확인한다.
SHOW FULL TABLES;
SHOW TRIGGERS;
SHOW EVENTS;
SELECT ROUTINE_NAME, ROUTINE_TYPE
FROM information_schema.ROUTINES
WHERE ROUTINE_SCHEMA = DATABASE();

-- 2. 삭제: 백업·외부 의존성 확인 및 새 코드 적용 후에만 수동 실행한다.
-- 아래 이름은 로컬 생성 SQL 기준이다. 실제 DB의 테이블 대소문자와 맞춘다.
-- 컬럼이 이미 없다면 ALTER 문을 건너뛴다.
ALTER TABLE `users` DROP COLUMN `BIRTH_DATE`;
DROP TABLE IF EXISTS `USER_SANCTIONS`;
DROP TABLE IF EXISTS `USER_LOGIN_SECURITY`;
DROP TABLE IF EXISTS `tax_process_logs`;

-- 3. 사후 확인: 두 조회 모두 0행이어야 한다.
SELECT TABLE_NAME, COLUMN_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND LOWER(TABLE_NAME) = 'users'
  AND UPPER(COLUMN_NAME) = 'BIRTH_DATE';

SELECT TABLE_NAME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND LOWER(TABLE_NAME) IN ('user_sanctions', 'user_login_security', 'tax_process_logs');
