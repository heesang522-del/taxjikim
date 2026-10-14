// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useState } from 'react';
import { Button, Card, Segmented, SelectField, TextField } from '../common/index';
import { INCOME_CATEGORIES, EXPENSE_CATEGORIES } from '../../data/options';
import { YEAR } from '../../data/taxConstants';
import { useTax } from '../../store/useTaxStore';
import { useToast } from '../../store/useToastStore';

const KINDS = [{ value: 'in', label: '수입' }, { value: 'out', label: '지출', tone: 'dark' }];

export default function EntryForm() {
  const { addRow, profile } = useTax();
  const toast = useToast();
  const [kind, setKind] = useState('in');
  const [date, setDate] = useState(`${YEAR}-09-30`);
  const [cat, setCat] = useState(INCOME_CATEGORIES[0]);
  const [memo, setMemo] = useState('');
  const [amt, setAmt] = useState('');
  const cats = kind === 'in' ? INCOME_CATEGORIES : EXPENSE_CATEGORIES;

  const changeKind = (k) => { setKind(k); setCat((k === 'in' ? INCOME_CATEGORIES : EXPENSE_CATEGORIES)[0]); };
  const submit = (e) => {
    e.preventDefault();
    const value = parseInt(amt.replace(/\D/g, ''), 10);
    if (!value || !date) { toast('날짜와 금액을 입력하세요'); return; }
    addRow({ kind, m: Math.min(12, Number(date.slice(5, 7))), date, cat, memo: memo.trim() || '(내용 없음)', amt: value });
    setMemo(''); setAmt('');
    toast('내역을 추가했습니다');
  };

  return (
    <Card title="수입·지출 내역 직접 입력" icon="fileText">
      <form onSubmit={submit}>
        <div className="row">
          <div className="field" style={{ flex: '0 0 auto' }}><label>구분</label><Segmented label="구분" options={KINDS} value={kind} onChange={changeKind} /></div>
          <TextField id="fDate" label="날짜" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
          <SelectField id="fCat" label="카테고리" options={cats} value={cat} onChange={(e) => setCat(e.target.value)} />
        </div>
        <div className="row" style={{ marginTop: 10 }}>
          <TextField id="fMemo" label="내용" placeholder="예: 아이패드 구매" value={memo} onChange={(e) => setMemo(e.target.value)} className="wide" />
          <TextField id="fAmt" label={kind === 'in' && profile.auto33 ? '입금액(원)' : '금액(원)'} inputClassName="num" inputMode="numeric" placeholder="1,000,000" value={amt} onChange={(e) => setAmt(e.target.value)} />
          <Button type="submit" variant="soft">+ 내역 추가하기</Button>
        </div>
      </form>
    </Card>
  );
}
