// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { Button, Card, DataTable, Icon } from '../common/index';
import { man } from '../../utils/format';
import { useScenarios } from '../../utils/useScenarios';
import { MAX_SCENARIOS } from '../../data/options';
import { useToast } from '../../store/useToastStore';

export function ScenarioTable({ onLoad }) {
  const { scenarios, remove, isLoading, errorMessage, deletingId } = useScenarios();
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
      render: (s) => (Number.isFinite(s.extraDonation) ? man(s.extraDonation) : '—'),
    },
    {
      key: 'res',
      header: '예상 결과',
      align: 'right',
      num: true,
      render: (s) => {
        // DB에 저장한 당시 결과만 표시하며 목록에서 다시 계산하지 않는다.
        if (Number.isFinite(s.totalTax)) return `총세금 ${man(s.totalTax)}`;
        return '저장 결과 없음';
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
          disabled={deletingId !== null}
          onClick={async () => {
            const removed = await remove(s.simulationId);
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
        <DataTable columns={columns} rows={scenarios} rowKey={(s) => s.simulationId} />
      ) : (
        <p className="muted">
          {isLoading
            ? '저장한 시나리오를 불러오는 중입니다.'
            : '시뮬레이션에서 시나리오를 저장하면 여기서 A/B/C안을 나란히 볼 수 있습니다.'}
        </p>
      )}
      {errorMessage && <p role="alert">{errorMessage}</p>}
      {onLoad && <p className="small muted">불러오면 현재 분석을 기준으로 다시 계산합니다.</p>}
    </Card>
  );
}
