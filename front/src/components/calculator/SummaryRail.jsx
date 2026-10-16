// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { Button, Card, Icon, KeyValue } from '../common/index';
import { MONTH_NOW } from '../../data/taxConstants';
import { man } from '../../utils/format';
import { sumBy } from '../../utils/tax';
import { useTax, useTaxStore, getPdfCalculationIssue, getSelectedTaxRows } from '../../store/useTaxStore';
import { useState } from 'react';

export default function SummaryRail({ onCalculate }) {
  const { rows, allRows, profile, inputMethod, evidenceDocuments } = useTax();
  const [calculationError, setCalculationError] = useState('');
  const [showConfirmation, setShowConfirmation] = useState(false);
  const pending = allRows.filter((row) => row.needsReview && !row.excluded).length;
  const handleCalculate = (confirmed = false) => {
    // 클릭 시점의 최신 선택값으로 검증하고 확인이 필요한 이유를 버튼 옆에 표시한다.
    const state = useTaxStore.getState();
    const selected = getSelectedTaxRows(state);
    if (!selected.length) {
      setShowConfirmation(false);
      setCalculationError(
        '계산에 포함할 내역이 없습니다. 인식된 날짜·기간, 구분, 금액과 계산 포함 선택을 확인해 주세요.'
      );
      return;
    }
    const issue = inputMethod === 'file' ? getPdfCalculationIssue(state, false) : '';
    if (issue) {
      setShowConfirmation(false);
      setCalculationError(issue);
      return;
    }
    if (inputMethod === 'file' && !confirmed && getPdfCalculationIssue(state)) {
      setCalculationError('');
      setShowConfirmation(true);
      return;
    }
    if (inputMethod === 'file' && confirmed) {
      // 사용자가 확인 버튼을 누른 문서만 최종 확인 상태로 바꾼다.
      const keys = new Set(selected.map((row) => row.sourceFileKey));
      state.evidenceDocuments
        .filter((entry) => keys.has(entry.sourceFileKey))
        .forEach((entry) => state.setPdfDocumentReviewed(entry.sourceFileKey, true));
    }
    setCalculationError('');
    setShowConfirmation(false);
    onCalculate();
  };
  const ti = sumBy(
    rows.filter((r) => r.kind === 'in'),
    (r) => r.amt
  );
  const te = sumBy(
    rows.filter((r) => r.kind === 'out'),
    (r) => r.amt
  );
  return (
    <Card as="aside" className="rail" title={`지금까지 입력 (1~${MONTH_NOW}월)`} icon="barChart">
      <p className="small muted" style={{ marginBottom: 12 }}>
        {inputMethod === 'file' ? 'PDF 내역 기준' : '직접 입력 내역 기준'}
      </p>
      <KeyValue label="수입 합계">{man(ti)}</KeyValue>
      <KeyValue label={inputMethod === 'file' ? '선택한 지출 합계' : '경비 합계'}>{man(te)}</KeyValue>
      <KeyValue label="원천징수(3%)">{profile.auto33 ? man(ti * 0.03) : '-'}</KeyValue>
      <KeyValue label="순 사업소득" total>
        {man(ti - te)}
      </KeyValue>
      <p className="small muted" style={{ marginBlock: 12 }}>
        연말 전체 금액은 최근 6개월 평균으로 예측해서 계산합니다. 10~12월 실제 금액을 넣을수록 정확해집니다.
      </p>
      <Button variant="primary" size="lg" block onClick={() => handleCalculate(false)}>
        <Icon name="calculator" /> {inputMethod === 'file' ? 'PDF 내역으로 계산' : '직접 입력 내역 분석'}
      </Button>
      {showConfirmation && (
        <section
          role="region"
          aria-label="PDF 계산 최종 확인"
          style={{ marginTop: 12, padding: 12, border: '1px solid var(--border-soft)', borderRadius: 8 }}
        >
          <p style={{ fontWeight: 600 }}>선택한 {rows.length}건으로 계산할까요?</p>
          <p className="small">
            원본과 금액·기간을 대조하고, 선택한 지출의 사업 관련성을 확인해 주세요. 카드 사용액이 자동으로 공제액이나
            사업 경비로 확정되는 것은 아닙니다.
          </p>
          <Button variant="primary" size="sm" block onClick={() => handleCalculate(true)}>
            원본·사업 관련성을 확인하고 계산
          </Button>
          <Button variant="ghost" size="sm" block onClick={() => setShowConfirmation(false)}>
            돌아가서 수정
          </Button>
        </section>
      )}
      {calculationError && (
        <p className="small" role="alert" style={{ marginTop: 10 }}>
          {calculationError}
        </p>
      )}
      {pending > 0 && <p className="small muted">확인 대기 {pending}건은 날짜·구분·금액 확인이 필요합니다.</p>}
      {!rows.length && (
        <p className="small muted" role="status" style={{ marginTop: 10 }}>
          {allRows.length
            ? pending
              ? '불러온 내역의 날짜·기간, 구분, 금액을 확인해 주세요.'
              : '계산에 포함할 내역을 한 건 이상 선택해 주세요.'
            : inputMethod === 'file'
              ? evidenceDocuments.length
                ? '연말정산 증빙은 별도 분석 결과로 표시됩니다. 현재 버튼은 장부 거래 계산용이며 증빙 세액 계산은 아직 지원하지 않습니다.'
                : 'PDF에서 인식된 장부 거래가 없습니다. 계산할 내역이 없어 버튼을 사용할 수 없습니다.'
              : '내역을 한 건 이상 입력해 주세요.'}
        </p>
      )}
    </Card>
  );
}
