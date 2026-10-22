'use no memo';

import { useState } from 'react';

import { Button, Card, Icon, KeyValue } from '../common/index';

import { useTax, useTaxStore, getPdfCalculationIssue, getSelectedTaxRows } from '../../store/useTaxStore';

function formatMoney(value) {
  const amount = Number(value ?? 0);

  return `${amount.toLocaleString('ko-KR')}원`;
}

export default function SummaryRail({ onCalculate }) {
  const { rows, inputMethod, profile } = useTax();

  const [calculationError, setCalculationError] = useState('');

  // 현재 선택되어 계산에 반영되는 거래만 합산한다.
  const income = rows.filter((row) => row.kind === 'in').reduce((sum, row) => sum + Number(row.amt ?? 0), 0);

  const expense = rows.filter((row) => row.kind === 'out').reduce((sum, row) => sum + Number(row.amt ?? 0), 0);

  // 수입·지출 구분이 없는 거래는 별도로 합산한다.
  const unknown = rows
    .filter((row) => row.kind !== 'in' && row.kind !== 'out')
    .reduce((sum, row) => sum + Number(row.amt ?? 0), 0);

  const autoWithholding = profile?.auto33 === true;

  const handleCalculate = () => {
    const state = useTaxStore.getState();

    const selected = getSelectedTaxRows(state);

    if (!selected.length) {
      setCalculationError('계산에 반영된 거래가 없습니다. PDF를 업로드하거나 거래를 입력해주세요.');
      return;
    }

    if (state.inputMethod === 'file') {
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
        {inputMethod === 'file' ? '자동 반영된 PDF 거래 기준' : '직접 입력한 거래 기준'}
      </p>

      <KeyValue label="반영된 거래">{rows.length}건</KeyValue>

      <KeyValue label="수입 합계">{formatMoney(income)}</KeyValue>

      <KeyValue label="지출 합계">{formatMoney(expense)}</KeyValue>

      <KeyValue label="미분류 합계">{formatMoney(unknown)}</KeyValue>

      <KeyValue label="원천징수(3.3%)">{autoWithholding ? '입금액 기준 자동 계산 적용' : '미적용'}</KeyValue>

      <KeyValue label="수입·지출 차액" total>
        {formatMoney(income - expense)}
      </KeyValue>

      <Button variant="primary" size="lg" block onClick={handleCalculate}>
        <Icon name="calculator" />

        {inputMethod === 'file' ? 'PDF 내역으로 계산' : '직접 입력 내역 분석'}
      </Button>

      {calculationError && (
        <p className="small" role="alert" style={{ marginTop: 10 }}>
          {calculationError}
        </p>
      )}
    </Card>
  );
}
