// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { Card } from '../../common/index';
import { man } from '../../../utils/format';

/** 총수입에서 기납부세액 차감 전 총 세금까지의 산출 근거 */
export function TaxChain({ result, usesYellow }) {
  const steps = [
    ['총수입', '연말 예측 포함', result.income],
    ['필요경비', '실제 경비 기준', -result.expense],
    ['사업소득금액', '', result.biz, true],
    ['소득공제', `기본공제 150만원${usesYellow ? ' + 노란우산' : ''}`, -result.deduction],
    ['과세표준', '', result.taxable, true],
    ['산출세액', `세율 ${Math.round(result.rate * 100)}% 적용`, result.tax, true],
    ['세액공제', '표준세액공제(예시)', -result.credit],
    ['소득세', '산출세액에서 세액공제 차감', result.net, true],
    ['지방소득세', '소득세 결정세액의 10%', result.local],
    ['총 세금', '소득세 + 지방소득세 · 기납부세액 차감 전', result.total, true],
  ];
  return (
    <Card title="산출 근거" icon="fileText">
      <div className="chain">
        {steps.map(([name, sub, v, hl]) => (
          <div key={name} className={`st ${hl ? 'hl' : ''}`}>
            <div>
              <b>{name}</b>
              <small>{sub}</small>
            </div>
            <span className="num">
              {v < 0 ? '−' : ''}
              {man(Math.abs(v))}
            </span>
          </div>
        ))}
      </div>
    </Card>
  );
}
