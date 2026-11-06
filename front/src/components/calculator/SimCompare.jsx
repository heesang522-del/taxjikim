// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { Card, KeyValue } from '../common/index';
import { man } from '../../utils/format';

// 계산은 페이지에서 요청하고, 이 컴포넌트는 비교 결과만 표시한다.
export function SimCompare({ base, scenario, isLoading, errorMessage }) {
  if (errorMessage)
    return (
      <Card title="현재 결과와 비교" icon="pieChart">
        <p role="alert">{errorMessage}</p>
      </Card>
    );
  if (isLoading || !base || !scenario)
    return (
      <Card title="현재 결과와 비교" icon="pieChart">
        <p>계산 중입니다.</p>
      </Card>
    );
  const d = base.totalTax - scenario.totalTax;
  const color = d > 0 ? 'var(--success)' : d < 0 ? 'var(--danger)' : 'var(--text-muted)';
  return (
    <Card as="aside" className="rail" title="현재 결과와 비교" icon="pieChart">
      <div className="cmp">
        <div className="now">
          <p className="small muted">현재</p>
          <p className="v">{man(base.totalTax)}</p>
          <p className="small">총세금</p>
        </div>
        <div className="what">
          <p className="small muted">시나리오</p>
          <p className="v">{man(scenario.totalTax)}</p>
          <p className="small">총세금</p>
        </div>
      </div>
      <p style={{ marginBlock: '14px 6px' }}>
        총세금 {d > 0 ? '감소' : d < 0 ? '증가' : '변화 없음'}{' '}
        <span className="delta" style={{ color }}>
          {man(Math.abs(d))}
        </span>
      </p>
      <KeyValue label="과세표준">
        {man(base.taxBase)} → {man(scenario.taxBase)}
      </KeyValue>
      <KeyValue label="산출세액">
        {man(base.assessedTax)} → {man(scenario.assessedTax)}
      </KeyValue>
      <KeyValue label="적용 세율">
        {Math.round(base.taxRate * 100)}% → {Math.round(scenario.taxRate * 100)}%
      </KeyValue>
      <KeyValue label="일반기부금 인정액">
        {man(base.generalDonationAllowed)} → {man(scenario.generalDonationAllowed)}
      </KeyValue>
      <p className="small muted" style={{ marginTop: 10 }}>
        추가 지출은 경비로 인정되는 경우만 효과가 있습니다.
      </p>
    </Card>
  );
}
