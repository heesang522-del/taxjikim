# 분석 기본정보 DB 변경 방법

2026-11-03 입력 화면·업로드 저장 코드 변경에 따른 안내다. 이 문서의 SQL은 자동 실행되지 않는다.
기존 데이터가 있는 DB는 테이블을 재생성하지 않고 ALTER로 변경한다. DB 담당자가 대상 DB와 백업을 확인한 후 실행한다.

## 먼저 현재 컬럼 확인

```sql
SELECT COLUMN_NAME, COLUMN_TYPE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'tax_inputs'
ORDER BY ORDINAL_POSITION;
```

## 기존 단일 기부금 컬럼이 있는 경우

`DONATION_AMOUNT`가 있고 새 기부금 두 컬럼이 아직 없는 기존 스키마에서 한 번 실행한다.
이미 두 컬럼으로 변경된 DB에는 실행하지 않는다.

```sql
ALTER TABLE tax_inputs
    CHANGE COLUMN DONATION_AMOUNT GENERAL_DONATION_AMOUNT
        DECIMAL(15,2) NOT NULL DEFAULT 0 COMMENT '일반기부금액 (종교단체 제외)',
    ADD COLUMN SPECIAL_DONATION_AMOUNT
        DECIMAL(15,2) NOT NULL DEFAULT 0 COMMENT '특례기부금액';
```

기존 단일 입력은 종교단체 외 복지단체 기부금을 받던 계약이므로 일반기부금으로 보존하고, 특례기부금은 0원으로 시작한다.
다른 용도로 단일 기부금을 저장했던 DB라면 그대로 옮기지 말고 데이터 의미를 먼저 확인한다.

## 제거할 입력 컬럼

위 컬럼 조회 결과에서 존재하는 컬럼에 대해서만 해당 문장을 실행한다. 없는 컬럼의 DROP은 건너뛴다.
나이·감면·지역 값은 더 이상 입력·계산에 사용하지 않으며, DROP을 실행하면 해당 값은 삭제된다.

```sql
ALTER TABLE tax_inputs DROP COLUMN AGE;
ALTER TABLE tax_inputs DROP COLUMN STARTUP_RELIEF_APPLIED;
ALTER TABLE tax_inputs DROP COLUMN BUSINESS_REGION;
```

## 변경 후 확인

- `GENERAL_DONATION_AMOUNT`와 `SPECIAL_DONATION_AMOUNT`는 각각 DECIMAL(15,2), NOT NULL, 기본값 0이다.
- 업로드 Mapper의 INSERT·SELECT·잠금 조회·저장 목록 조회·UPDATE가 새 컬럼명을 사용한다.
- 기존 단일 기부금 컬럼과 제거한 입력 필드는 DTO·검증·저장 정규화 코드에서 사용하지 않는다.
- 신규 기본정보 저장 → 동일 분석번호로 조회 → 두 기부금 금액 수정 → 재조회 → 계산 결과 조회 순서로 확인한다.
- PDF 추출·ML 분류와 실제 분석번호의 결과 페이지 연결은 이번 DB 변경과 별도로 검증한다. 결과 페이지의 테스트 번호를 바꾼 것으로 간주하지 않는다.
