// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { Button, Card } from '../common/index';
import { ROUTES } from '../../utils/routes';
import { man } from '../../utils/format';

export default function ActionList({ gainYellow, gainExpense }) {
  return (
    <Card title="다음에 할 일" icon="trendingUp" aside={<span className="muted small">예상 절감 효과 순</span>}>
      <div className="act"><div style={{ flex: 1 }}><b>노란우산공제 300만원 납입</b><p className="small muted">소득공제가 늘어 과세표준이 줄어듭니다. 한도는 소득 구간별로 다르니 확인하세요.</p></div><span className="gain">−{man(gainYellow)}</span></div>
      <div className="act"><div style={{ flex: 1 }}><b>연말 전 필요한 장비·소프트웨어 100만원 구매</b><p className="small muted">업무 관련 지출은 증빙을 남기면 경비로 인정됩니다.</p></div><span className="gain">−{man(gainExpense)}</span></div>
      <div className="act"><div style={{ flex: 1 }}><b>누락된 경비 확인</b><p className="small muted">크리에이터는 장비, 교육·도서비, 통신비를 많이 반영합니다. 입력에 없는 항목을 확인해 보세요.</p></div><Button size="sm" to={ROUTES.calcInput}>입력 수정</Button></div>
    </Card>
  );
}
