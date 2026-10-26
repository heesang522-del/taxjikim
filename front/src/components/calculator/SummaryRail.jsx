'use no memo';

import { useRef, useState } from 'react';

import { Button, Card, KeyValue } from '../common/index';

import {
  getTaxReviewSignature,
} from '../../api/taxApi';

import {
  useTax,
  useTaxStore,
  parseTaxAmount,
} from '../../store/useTaxStore';

import {
  getTaxInputSummary,
} from '../../utils/taxInputSummary';

function money(value) {
  return `${Number(value ?? 0).toLocaleString('ko-KR')}원`;
}

export function SummaryRail({ onCalculate }) {
  const state = useTaxStore();
  const { rows } = useTax();

  const movingRef = useRef(false);
  const [error, setError] = useState('');

  const busy = state.taxSaveBusy === true;

  const {
    income,
    expense,
    unclassified,
  } = getTaxInputSummary(
      rows.filter((row) => row.amountBasis !== 'DEDUCTION_ELIGIBLE'),
  );

  const evidenceRows = (state.evidenceDocuments ?? []).flatMap(
      (document) => document.rows,
  );

  const evidenceTotal = evidenceRows.reduce(
      (sum, row) => sum + (parseTaxAmount(row.amount) ?? 0),
      0,
  );

  async function handleCalculate() {
    if (busy || movingRef.current) return;

    setError('');

    const current = useTaxStore.getState();

    if (
        !current.taxLastSavedSignature
        || current.taxLastSavedSignature !== getTaxReviewSignature(current)
    ) {
      setError('내역 확인 팝업의 결정 버튼으로 최신 내용을 저장해주세요.');
      return;
    }

    if (typeof current.profile.businessRegistered !== 'boolean') {
      setError('사업자등록 여부를 선택하고 다시 결정으로 저장해주세요.');
      return;
    }

    if (
        current.profile.businessRegistered === false
        && parseTaxAmount(current.profile.age) === null
    ) {
      setError('현재 만 나이를 입력하고 다시 결정으로 저장해주세요.');
      return;
    }

    if (
        current.profile.businessRegistered
        && typeof current.profile.startupReliefApplied !== 'boolean'
    ) {
      setError('창업 세액감면 적용 여부를 입력해주세요.');
      return;
    }

    if (
        current.profile.businessRegistered
        && current.profile.startupReliefApplied
        && ![
          'CAPITAL_CONGESTION',
          'CAPITAL_OTHER',
          'NON_CAPITAL',
        ].includes(current.profile.businessRegion)
    ) {
      setError('창업 당시 사업장 지역을 선택해주세요.');
      return;
    }

    movingRef.current = true;

    try {
      await onCalculate?.();
    } catch (cause) {
      setError(cause.message ?? '결과 화면으로 이동하지 못했습니다.');
    } finally {
      movingRef.current = false;
    }
  }

  return (
      <Card as="aside" className="rail" title="거래 합계">
        <KeyValue label="일반 거래">
          {state.manualRows.length + state.fileRows.length}건
        </KeyValue>

        <KeyValue label="월별 증빙">
          {evidenceRows.length}건
        </KeyValue>

        <KeyValue label="수입 합계">{money(income)}</KeyValue>
        <KeyValue label="지출 합계">{money(expense)}</KeyValue>
        <KeyValue label="일반 거래 미분류 합계">
          {money(unclassified)}
        </KeyValue>

        {evidenceRows.length > 0 && (
            <>
              <KeyValue label="증빙 공제대상금액">
                {money(evidenceTotal)}
              </KeyValue>

              <p className="small muted">
                공제대상금액은 실제 출금액과 합산하지 않습니다.
              </p>
            </>
        )}

        <KeyValue label="수입·지출 차액" total>
          {money(income - expense)}
        </KeyValue>

        <p className="small muted">
          결정 버튼에서 새 분석 묶음으로 저장합니다.
        </p>

        <Button
            type="button"
            variant="primary"
            block
            disabled={busy}
            onClick={handleCalculate}
        >
          {busy ? 'DB 저장 중…' : '분석 결과 보기'}
        </Button>

        {error && <p role="alert">{error}</p>}
      </Card>
  );
}