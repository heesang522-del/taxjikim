// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { Card, KeyValue } from '../common/index';
import { man } from '../../utils/format';
import { useTax } from '../../store/useTaxStore';
import { useTaxResult } from '../../utils/useTaxResult';

export default function SimCompare() {
  const { sim } = useTax();
  const base = useTaxResult();
  const scenario = useTaxResult(sim);
  const d = base.total - scenario.total;
  const color = d > 0 ? 'var(--success)' : d < 0 ? 'var(--danger)' : 'var(--text-muted)';
  return (
    <Card as="aside" className="rail" title="현재 결과와 비교" icon="pieChart">
      <div className="cmp">
        <div className="now"><p className="small muted">현재</p><p className="v">{man(Math.abs(base.total))}</p><p className="small">{base.total < 0 ? '환급' : '추가납부'}</p></div>
        <div className="what"><p className="small muted">시나리오</p><p className="v">{man(Math.abs(scenario.total))}</p><p className="small">{scenario.total < 0 ? '환급' : '추가납부'}</p></div>
      </div>
      <p style={{ marginBlock: '14px 6px' }}>환급액 변화 <span className="delta" style={{ color }}>{d > 0 ? '+' : d < 0 ? '−' : ''}{man(Math.abs(d))}</span></p>
      <KeyValue label="과세표준">{man(base.taxable)} → {man(scenario.taxable)}</KeyValue>
      <KeyValue label="산출세액">{man(base.tax)} → {man(scenario.tax)}</KeyValue>
      <KeyValue label="적용 세율">{Math.round(base.rate * 100)}% → {Math.round(scenario.rate * 100)}%</KeyValue>
      <p className="small muted" style={{ marginTop: 10 }}>추가 지출은 경비로 인정되는 경우만 효과가 있습니다.</p>
    </Card>
  );
}
