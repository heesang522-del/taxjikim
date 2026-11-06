// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { Button, Card, DataTable, Icon } from '../common/index';
import { man } from '../../utils/format';
import { calcTax } from '../../utils/tax';
import { useTax } from '../../store/useTaxStore';
import { useScenarios } from '../../utils/useScenarios';
import { MAX_SCENARIOS } from '../../data/options';
import { useToast } from '../../store/useToastStore';

export function ScenarioTable({ onLoad }) {
  const { rows, profile } = useTax();
  const { scenarios, remove } = useScenarios();
  const toast = useToast();
  const columns = [
    { key: 'name', header: '이름' },
    { key: 'extraInc', header: '추가 수입', align: 'right', num: true, render: (s) => man(s.extraInc) },
    { key: 'extraExp', header: '추가 지출', align: 'right', num: true, render: (s) => man(s.extraExp) },
    {
      key: 'extraDonation',
      header: '추가 기부금',
      align: 'right',
      num: true,
      render: (s) => (Number.isFinite(s.extraDonation) ? man(s.extraDonation) : '기존 형식'),
    },
    {
      key: 'res',
      header: '예상 결과',
      align: 'right',
      num: true,
      render: (s) => {
        // 새 시나리오는 저장 당시 백엔드 총세금을 사용한다. 기존 기록 계산은 유지한다.
        if (Number.isFinite(s.totalTax)) return `총세금 ${man(s.totalTax)}`;
        const r = calcTax(rows, profile.auto33, s);
        return `${r.total < 0 ? '환급 ' : '납부 '}${man(Math.abs(r.total))}`;
      },
    },
    {
      key: 'del',
      header: '',
      render: (s) => (
        <Button
          size="sm"
          variant="danger"
          className="icon"
          aria-label="삭제"
          onClick={() => {
            const removed = remove(s.name);
            toast(removed ? '시나리오를 삭제했습니다' : '시나리오를 삭제하지 못했습니다');
          }}
        >
          <Icon name="trash" size={16} />
        </Button>
      ),
    },
  ];
  // 불러오기를 전달한 시뮬레이션 화면에서만 적용 버튼을 표시한다.
  if (onLoad) {
    columns.splice(columns.length - 1, 0, {
      key: 'load',
      header: '',
      render: (scenario) => (
        <Button size="sm" onClick={() => onLoad(scenario)} disabled={!Number.isFinite(scenario.extraDonation)}>
          불러오기
        </Button>
      ),
    });
  }
  return (
    <Card
      title="저장한 시나리오"
      icon="scale"
      aside={
        <span className="small muted">
          {scenarios.length}/{MAX_SCENARIOS}
        </span>
      }
    >
      {scenarios.length ? (
        <DataTable columns={columns} rows={scenarios} rowKey={(s) => s.name} />
      ) : (
        <p className="muted">시뮬레이션에서 시나리오를 저장하면 여기서 A/B/C안을 나란히 볼 수 있습니다.</p>
      )}
      {onLoad && (
        <p className="small muted">
          불러오면 현재 분석을 기준으로 다시 계산합니다. 기존 노란우산 형식은 불러오기에서 제외됩니다.
        </p>
      )}
    </Card>
  );
}
