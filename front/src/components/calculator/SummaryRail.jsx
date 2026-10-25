'use no memo';

import { useState } from 'react';

import { Button, Card, Icon, KeyValue } from '../common/index';

import {
  useTax,
  useTaxStore,
  getPdfCalculationIssue,
  getSelectedTaxRows,
  parseTaxAmount,
} from '../../store/useTaxStore';
import { getTaxInputSummary } from '../../utils/taxInputSummary';

function formatMoney(value) {
  const amount = Number(value ?? 0);

  return `${amount.toLocaleString('ko-KR')}원`;
}

export function SummaryRail({ onCalculate }) {
  const { rows } = useTax();

  const [calculationError, setCalculationError] = useState('');

  // 선택한 거래의 실제 입출금액을 기준으로 화면 합계를 표시한다.
  const { income, expense, unknown, unclassified, missingAmountCount } = getTaxInputSummary(rows);

  const handleCalculate = () => {
    const state = useTaxStore.getState();
    const { profile } = state;

    if (typeof profile.businessRegistered !== 'boolean') {
      setCalculationError('사업자등록 여부를 선택해주세요.');
      return;
    }
    if (profile.businessRegistered === false && parseTaxAmount(profile.age) === null) {
      setCalculationError('현재 만 나이를 0 이상의 정수로 입력해주세요.');
      return;
    }
    if (parseTaxAmount(profile.dependentCount) === null || parseTaxAmount(profile.donationAmount) === null) {
      setCalculationError('공제 대상 가족 수와 기부금을 0 이상의 정수로 입력해주세요.');
      return;
    }
    if (profile.businessRegistered && typeof profile.startupReliefApplied !== 'boolean') {
      setCalculationError('올해 창업 세액감면 적용 여부를 선택해주세요.');
      return;
    }
    if (
      profile.businessRegistered &&
      profile.startupReliefApplied &&
      !['CAPITAL_CONGESTION', 'CAPITAL_OTHER', 'NON_CAPITAL'].includes(profile.businessRegion)
    ) {
      setCalculationError('창업 당시 사업장 지역을 선택해주세요.');
      return;
    }

    const selected = getSelectedTaxRows(state);

    if (!selected.length) {
      setCalculationError('계산에 반영된 거래가 없습니다. PDF를 업로드하거나 거래를 입력해주세요.');
      return;
    }

    if (state.fileRows.length > 0) {
      // 사용자 검토창 없이 중복 합산 여부만 검사한다.
      const issue = getPdfCalculationIssue(state);

      if (issue) {
        setCalculationError(issue);
        return;
      }
    }

    setCalculationError('');

    onCalculate?.();
  };

  return (
    <Card as="aside" className="rail" title="거래 합계" icon="barChart">
      <p className="small muted" style={{ marginBottom: 12 }}>
        직접 입력·PDF 거래 내역 기준
      </p>

      <KeyValue label="계산할 거래">{rows.length}건</KeyValue>

      <KeyValue label="수입 합계">{formatMoney(income)}</KeyValue>

      <KeyValue label="지출 합계">{formatMoney(expense)}</KeyValue>

      <KeyValue label="미분류 합계">{formatMoney(unclassified)}</KeyValue>
      {unknown > 0 && <KeyValue label="수입·지출 구분 미확인">{formatMoney(unknown)}</KeyValue>}
      <p className="small muted">실제 입출금액 기준입니다. 미분류 금액은 위 수입·지출 합계에 포함된 금액입니다.</p>
      {missingAmountCount > 0 && (
        <p className="small" role="status">
          실제 입출금액을 확인할 수 없는 {missingAmountCount}건은 이 합계에서 제외했습니다.
        </p>
      )}

      <KeyValue label="수입·지출 차액" total>
        {formatMoney(income - expense)}
      </KeyValue>

      <Button variant="primary" size="lg" block onClick={handleCalculate}>
        <Icon name="calculator" />
        분석 결과 보기
      </Button>

      {calculationError && (
        <p className="small" role="alert" style={{ marginTop: 10 }}>
          {calculationError}
        </p>
      )}
    </Card>
  );
}
