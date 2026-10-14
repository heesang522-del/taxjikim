// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useState } from 'react';
import { Button, Card, Pill, Segmented, SelectField, TextField } from '../common/index';
import { INCOME_CATEGORIES, EXPENSE_CATEGORIES } from '../../data/options';
import { useTax } from '../../store/useTaxStore';
import { useToast } from '../../store/useToastStore';

const KINDS = [{ value: 'in', label: '수입' }, { value: 'out', label: '지출', tone: 'dark' }];

function PendingRow({ row, onResolve, onDiscard }) {
  const toast = useToast();
  const [kind, setKind] = useState(row.kind || 'out');
  const cats = kind === 'in' ? INCOME_CATEGORIES : EXPENSE_CATEGORIES;
  const [date, setDate] = useState(row.date || '');
  const [cat, setCat] = useState(row.cat && cats.includes(row.cat) ? row.cat : cats[0]);
  const [memo, setMemo] = useState(row.memo || '');
  const [amt, setAmt] = useState(row.amt ? String(row.amt) : '');

  const changeKind = (k) => { setKind(k); setCat((k === 'in' ? INCOME_CATEGORIES : EXPENSE_CATEGORIES)[0]); };

  const confirm = () => {
    const value = parseInt(String(amt).replace(/\D/g, ''), 10);
    if (!value || !date) { toast('날짜와 금액을 확인하세요'); return; }
    onResolve({ kind, date, cat, memo: memo.trim() || '(내용 없음)', amt: value });
  };

  return (
    <div style={{ padding: '14px 0', borderBottom: '1px solid var(--border-soft)' }}>
      <div className="row" style={{ marginBottom: 8 }}>{row.reasons.map((r) => <Pill key={r} tone="warn">{r}</Pill>)}</div>
      <div className="row">
        <div className="field" style={{ flex: '0 0 auto' }}><label>구분</label><Segmented label="구분" options={KINDS} value={kind} onChange={changeKind} /></div>
        <TextField id={`pd_${row.id}`} label="날짜" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
        <SelectField id={`pc_${row.id}`} label="카테고리" options={cats} value={cat} onChange={(e) => setCat(e.target.value)} />
      </div>
      <div className="row" style={{ marginTop: 10 }}>
        <TextField id={`pm_${row.id}`} label="내용" value={memo} onChange={(e) => setMemo(e.target.value)} className="wide" />
        <TextField id={`pa_${row.id}`} label="금액(원)" inputClassName="num" inputMode="numeric" placeholder="1,000,000" value={amt} onChange={(e) => setAmt(e.target.value)} />
      </div>
      <div className="row" style={{ marginTop: 10, justifyContent: 'flex-end' }}>
        <Button size="sm" onClick={onDiscard}>무시</Button>
        <Button size="sm" variant="primary" onClick={confirm}>내역에 추가</Button>
      </div>
    </div>
  );
}

/** CSV 업로드에서 구분·카테고리·날짜·금액을 확실히 읽지 못한 내역을 따로 확인하는 카드 */
export default function ImportReview() {
  const { pendingRows, resolvePendingRow, discardPendingRow } = useTax();
  const toast = useToast();
  if (!pendingRows.length) return null;

  return (
    <Card title="확인이 필요한 내역" icon="shieldAlert" aside={<Pill tone="warn">{pendingRows.length}건</Pill>}>
      <p className="small muted" style={{ marginBottom: 4 }}>업로드한 파일에서 구분·카테고리·날짜·금액 중 확실히 읽지 못한 내역입니다. 값을 확인하고 추가하거나 무시하세요. 계산에는 포함되지 않습니다.</p>
      {pendingRows.map((row) => (
        <PendingRow
          key={row.id}
          row={row}
          onResolve={(values) => { resolvePendingRow(row.id, values); toast('내역을 추가했습니다'); }}
          onDiscard={() => { discardPendingRow(row.id); toast('내역을 무시했습니다'); }}
        />
      ))}
    </Card>
  );
}
