// 기존 결과 화면의 React Compiler 제외 설정을 유지한다.
'use no memo';

import { Card } from '../../common/index';
import { YEAR } from '../../../data/taxConstants';
import { man } from '../../../utils/format';

// 페이지가 한 번 조회한 계산 결과를 세 요약 카드에 표시한다.
export function SummaryCards({ result, isLoading = false, errorMessage = '' }) {
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
        </Card>
        <Card className="stat">
          <p className="lbl">총 수입</p>
          <p className="big">{hasResult ? man(result.totalIncome) : '—'}</p>
          <p className="sub ok">실제·예상 금액 합계</p>
        </Card>
        <Card className="stat">
          <p className="lbl">총 지출</p>
          <p className="big">{hasResult ? man(result.totalExpense) : '—'}</p>
          <p className="sub ok">실제·예상 금액 합계</p>
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
