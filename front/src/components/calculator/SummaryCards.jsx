// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { Card } from '../common/index';
import { YEAR } from '../../data/taxConstants';
import { man } from '../../utils/format';

/** 결과 상단 3카드: 환급/추가납부(남색), 총 수입, 총 지출 */
export default function SummaryCards({ result }) {
  const refund = result.total < 0;
  return (
    <div className="grid g3">
      <Card className="stat dark">
        <p className="lbl">{refund ? '예상 환급액' : '예상 추가납부액'} ({YEAR}년 귀속, 지방소득세 포함)</p>
        <p className="big">{man(Math.abs(result.total))}</p>
        <p className="sub">적용 세율 {Math.round(result.rate * 100)}% · 과세표준 {man(result.taxable)}</p>
        <p className="sub">소득세 {man(result.net)} + 지방소득세 {man(result.local - result.localWithheld)}</p>
      </Card>
      <Card className="stat">
        <p className="lbl">총 수입</p>
        <p className="big">{man(result.income)}</p>
        <p className="sub ok">10~12월 예측 포함</p>
      </Card>
      <Card className="stat">
        <p className="lbl">총 지출 (필요경비)</p>
        <p className="big">{man(result.expense)}</p>
        <p className="sub">순 사업소득 {man(result.biz)}</p>
      </Card>
    </div>
  );
}
