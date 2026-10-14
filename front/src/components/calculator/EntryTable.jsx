// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { Button, Card, DataTable, Icon, Pill } from '../common/index';
import { won } from '../../utils/format';
import { useTax } from '../../store/useTaxStore';

export default function EntryTable() {
  const { rows, removeRow } = useTax();
  const sorted = [...rows].sort((a, b) => b.m - a.m || (a.kind === 'in' ? -1 : 1));
  const columns = [
    { key: 'kind', header: '구분', render: (r) => <Pill tone={r.kind === 'in' ? 'primary' : 'neutral'}>{r.kind === 'in' ? '수입' : '지출'}</Pill> },
    { key: 'date', header: '날짜', num: true, render: (r) => r.date.slice(5) },
    { key: 'cat', header: '카테고리' },
    { key: 'memo', header: '내용' },
    { key: 'amt', header: '금액', align: 'right', num: true, render: (r) => won(r.amt) },
    { key: 'del', header: '', render: (r) => <Button size="sm" variant="danger" className="icon" aria-label="삭제" onClick={() => removeRow(r.id)}><Icon name="trash" size={16} /></Button> },
  ];
  return (
    <Card title="입력한 내역" aside={<span className="muted small">{rows.length}건</span>}>
      <DataTable columns={columns} rows={sorted} rowKey={(r) => r.id} />
    </Card>
  );
}
