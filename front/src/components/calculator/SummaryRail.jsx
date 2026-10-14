// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { Button, Card, Icon, KeyValue } from '../common/index';
import { MONTH_NOW } from '../../data/taxConstants';
import { ROUTES } from '../../utils/routes';
import { man } from '../../utils/format';
import { sumBy } from '../../utils/tax';
import { useTax } from '../../store/useTaxStore';

export default function SummaryRail({ onCalculate }) {
  const { rows, profile } = useTax();
  const ti = sumBy(rows.filter((r) => r.kind === 'in'), (r) => r.amt);
  const te = sumBy(rows.filter((r) => r.kind === 'out'), (r) => r.amt);
  return (
    <Card as="aside" className="rail" title={`지금까지 입력 (1~${MONTH_NOW}월)`} icon="barChart">
      <KeyValue label="수입 합계">{man(ti)}</KeyValue>
      <KeyValue label="경비 합계">{man(te)}</KeyValue>
      <KeyValue label="원천징수(3%)">{profile.auto33 ? man(ti * 0.03) : '-'}</KeyValue>
      <KeyValue label="순 사업소득" total>{man(ti - te)}</KeyValue>
      <p className="small muted" style={{ marginBlock: 12 }}>연말 전체 금액은 최근 6개월 평균으로 예측해서 계산합니다. 10~12월 실제 금액을 넣을수록 정확해집니다.</p>
      <Button variant="primary" size="lg" block onClick={onCalculate} disabled={rows.length === 0}><Icon name="calculator" /> 계산하기 & 세액 산출</Button>
    </Card>
  );
}
