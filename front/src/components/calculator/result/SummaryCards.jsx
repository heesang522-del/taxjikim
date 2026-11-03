// 기존 결과 화면의 React Compiler 제외 설정을 유지한다.
'use no memo';

import { Card } from '../../common/index';
import { YEAR } from '../../../data/taxConstants';
import { man } from '../../../utils/format';

// 페이지가 한 번 조회한 계산 결과를 세 요약 카드에 표시한다.
export function SummaryCards({
  result,
  actualIncome,
  predictedIncome,
  actualExpense,
  predictedExpense,
  isLoading = false,
  errorMessage = '',
}) {
  const statusMessage = isLoading ? '계산 결과를 불러오는 중입니다.' : errorMessage;
  const hasResult = !statusMessage && result != null;

  return (
    <>
      <div className="grid g3">
        <Card className="stat dark">
          <p className="lbl">총 세금 ({result?.taxYear ?? YEAR}년 귀속, 지방소득세 포함)</p>
          <p className="big">{hasResult ? man(result.totalTax) : '—'}</p>
          {hasResult && (
            <>
              <p className="sub">
                적용 세율 {Math.round(result.taxRate * 100)}% · 과세표준 {man(result.taxBase)}
              </p>
              <p className="sub">
                소득세 {man(result.incomeTax)} + 지방소득세 {man(result.localIncomeTax)}
              </p>
              <p className="sub">수입·지출 차액 {man(result.totalIncome - result.totalExpense)}</p>
            </>
          )}
          {/* 배치 확인용 시안이며, 정산 전에는 임의의 납부 금액을 표시하지 않는다. */}
          <p className="sub" title="이미 낸 세금을 반영하는 정산 기능은 아직 준비 중입니다.">
            추가 납부 예상액 <strong>—</strong> · 준비 중
          </p>
        </Card>
        <Card className="stat">
          <p className="lbl">총 수입</p>
          <p className="big">{hasResult ? man(result.totalIncome) : '—'}</p>
          <p className="sub ok">실제·예상 금액 합계</p>
          {/* 서버가 구분한 실제·예상 합계를 표시하며 화면에서 다시 계산하지 않는다. */}
          <p className="sub">실제 {hasResult && actualIncome != null ? man(actualIncome) : '—'}</p>
          <p className="sub">예상 {hasResult && predictedIncome != null ? man(predictedIncome) : '—'}</p>
        </Card>
        <Card className="stat">
          <p className="lbl">총 지출</p>
          <p className="big">{hasResult ? man(result.totalExpense) : '—'}</p>
          <p className="sub ok">실제·예상 금액 합계</p>
          <p className="sub">실제 {hasResult && actualExpense != null ? man(actualExpense) : '—'}</p>
          <p className="sub">예상 {hasResult && predictedExpense != null ? man(predictedExpense) : '—'}</p>
        </Card>
      </div>
      {statusMessage && (
        <p className="hint" role={errorMessage ? 'alert' : 'status'}>
          {statusMessage}
        </p>
      )}
      {!statusMessage && !result && <p className="hint">표시할 계산 결과가 없습니다.</p>}
      <p className="hint">입력한 지출 전액을 필요경비로 가정합니다.</p>
    </>
  );
}
