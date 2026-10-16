// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { Card } from '../common/index';
import { man } from '../../utils/format';

/** 총수입 → 환급/추가납부(소득세 + 지방소득세) 산출 근거 */
export default function TaxChain({ result, usesYellow }) {
  const refund = result.total < 0;
  const steps = [
    ['총수입', '연말 예측 포함', result.income],
    ['필요경비', '실제 경비 기준', -result.expense],
    ['사업소득금액', '', result.biz, true],
    ['소득공제', `기본공제 150만원${usesYellow ? ' + 노란우산' : ''}`, -result.deduction],
    ['과세표준', '', result.taxable, true],
    ['산출세액', `세율 ${Math.round(result.rate * 100)}% 적용`, result.tax, true],
    ['세액공제', '표준세액공제(예시)', -result.credit],
    ['기납부세액', '3.3% 원천징수 중 소득세 3%', -result.withheld],
    ['지방소득세', '소득세 결정세액의 10%', result.local],
    ['기납부 지방소득세', '3.3% 원천징수 중 0.3%', -result.localWithheld],
    [refund ? '환급 예상액' : '추가납부 예상액', '소득세 + 지방소득세', Math.abs(result.total), true],
  ];
  return (
    <Card title="산출 근거" icon="fileText">
      <div className="chain">
        {steps.map(([name, sub, v, hl]) => (
          <div key={name} className={`st ${hl ? 'hl' : ''}`}>
            <div><b>{name}</b><small>{sub}</small></div>
            <span className="num">{v < 0 ? '−' : ''}{man(Math.abs(v))}</span>
          </div>
        ))}
      </div>
    </Card>
  );
}
