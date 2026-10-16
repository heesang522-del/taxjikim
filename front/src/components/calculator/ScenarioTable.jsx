// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { Button, Card, DataTable, Icon } from '../common/index';
import { man } from '../../utils/format';
import { calcTax } from '../../utils/tax';
import { useTax } from '../../store/useTaxStore';
import { useScenarios } from '../../utils/useScenarios';
import { MAX_SCENARIOS } from '../../data/options';
import { useToast } from '../../store/useToastStore';

export default function ScenarioTable() {
  const { rows, profile } = useTax();
  const { scenarios, remove } = useScenarios();
  const toast = useToast();
  const columns = [
    { key: 'name', header: '이름' },
    { key: 'extraExp', header: '추가 지출', align: 'right', num: true, render: (s) => man(s.extraExp) },
    { key: 'yellow', header: '노란우산', align: 'right', num: true, render: (s) => man(s.yellow) },
    { key: 'extraInc', header: '추가 수입', align: 'right', num: true, render: (s) => man(s.extraInc) },
    { key: 'res', header: '예상 결과', align: 'right', num: true, render: (s) => { const r = calcTax(rows, profile.auto33, s); return `${r.total < 0 ? '환급 ' : '납부 '}${man(Math.abs(r.total))}`; } },
    { key: 'del', header: '', render: (s) => <Button size="sm" variant="danger" className="icon" aria-label="삭제" onClick={() => { remove(s.name); toast('시나리오를 삭제했습니다'); }}><Icon name="trash" size={16} /></Button> },
  ];
  return (
    <Card title="저장한 시나리오" icon="scale" aside={<span className="small muted">{scenarios.length}/{MAX_SCENARIOS}</span>}>
      {scenarios.length
        ? <DataTable columns={columns} rows={scenarios} rowKey={(s) => s.name} />
        : <p className="muted">시뮬레이션에서 시나리오를 저장하면 여기서 A/B/C안을 나란히 볼 수 있습니다.</p>}
    </Card>
  );
}
