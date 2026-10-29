// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useEffect, useState } from 'react';
import { getTotalIncome, getTotalExpense } from '../../../api/taxApi';
import { Card } from '../../common/index';
import { YEAR } from '../../../data/taxConstants';
import { man } from '../../../utils/format';

/** 결과 상단 3카드: 기납부세액 차감 전 총 세금, 총 수입, 총 지출 */
export function SummaryCards({ result, taxInputId }) {
  const [totals, setTotals] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState('');

  // 분석번호가 바뀌면 서버에서 계산한 실제·예상 총수입과 총지출을 조회한다.
  useEffect(() => {
    if (taxInputId == null) return;
    let cancelled = false;
    const controller = new AbortController();

    // 두 합계가 모두 도착하면 카드에 함께 반영한다.
    async function loadTotals() {
      setIsLoading(true);
      setErrorMessage('');
      setTotals(null);
      try {
        const [income, expense] = await Promise.all([
          getTotalIncome(taxInputId, { signal: controller.signal }),
          getTotalExpense(taxInputId, { signal: controller.signal }),
        ]);
        if (!cancelled) {
          setTotals({ income: income.data.totalAmount, expense: expense.data.totalAmount });
        }
      } catch (error) {
        if (!cancelled) setErrorMessage(error.message || '합계를 불러오지 못했습니다.');
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    }

    loadTotals();
    // 이전 분석의 응답이 현재 카드 값을 덮어쓰지 않도록 요청을 정리한다.
    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [taxInputId]);

  // 분석번호 없이 쓰는 기존 화면은 전달받은 결과를 유지한다.
  const income = taxInputId == null ? result.income : totals?.income;
  const expense = taxInputId == null ? result.expense : totals?.expense;
  const statusMessage = taxInputId == null ? '' : isLoading ? '불러오는 중…' : errorMessage;

  return (
    <>
      <div className="grid g3">
        <Card className="stat dark">
          <p className="lbl">총 세금 ({YEAR}년 귀속, 지방소득세 포함)</p>
          <p className="big">{man(result.total)}</p>
          <p className="sub">
            적용 세율 {Math.round(result.rate * 100)}% · 과세표준 {man(result.taxable)}
          </p>
          <p className="sub">
            소득세 {man(result.net)} + 지방소득세 {man(result.local)}
          </p>
          <p className="sub">{!statusMessage && `수입·지출 차액 ${man(income - expense)}`}</p>
        </Card>
        <Card className="stat">
          <p className="lbl">총 수입</p>
          <p className="big" role={errorMessage ? 'alert' : undefined}>
            {statusMessage || man(income)}
          </p>
          <p className="sub ok">실제·예상 금액 합계</p>
        </Card>
        <Card className="stat">
          <p className="lbl">총 지출</p>
          <p className="big" role={errorMessage ? 'alert' : undefined}>
            {statusMessage || man(expense)}
          </p>
          <p className="sub ok">실제·예상 금액 합계</p>
        </Card>
      </div>
      <p className="hint">입력한 지출 전액을 필요경비로 가정합니다.</p>
    </>
  );
}
