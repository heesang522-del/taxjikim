'use no memo';

import { useState } from 'react';
import { Button, Card, Segmented, TextField } from '../common/index';
import { INCOME_TRANSACTION_CATEGORIES, EXPENSE_TRANSACTION_CATEGORIES } from '../../data/transactionCategories';
import { useTax, parseTaxAmount } from '../../store/useTaxStore';
import { useToast } from '../../store/useToastStore';
import FileUploader from './FileUploader';
import { calculatePreTaxIncome } from '../../utils/transactionAmount';

const KINDS = [
  { value: 'in', label: '수입' },
  { value: 'out', label: '지출', tone: 'dark' },
];

export function EntryForm() {
  const { addRow, profile } = useTax();
  const toast = useToast();
  const [kind, setKind] = useState('in');
  const [date, setDate] = useState(() => new Date().toLocaleDateString('sv-SE'));
  const [cat, setCat] = useState(INCOME_TRANSACTION_CATEGORIES[0].value);
  const [memo, setMemo] = useState('');
  const [amt, setAmt] = useState('');
  const [deductionRate, setDeductionRate] = useState('3.3');
  const preTaxIncome = calculatePreTaxIncome(parseTaxAmount(amt), deductionRate);
  const cats = kind === 'in' ? INCOME_TRANSACTION_CATEGORIES : EXPENSE_TRANSACTION_CATEGORIES;

  const handleChangeKind = (value) => {
    setKind(value);
    setCat((value === 'in' ? INCOME_TRANSACTION_CATEGORIES : EXPENSE_TRANSACTION_CATEGORIES)[0].value);
  };

  const handleSubmit = (event) => {
    event.preventDefault();
    const actualAmount = parseTaxAmount(amt);
    const grossAmount = calculatePreTaxIncome(actualAmount, deductionRate);
    if (!date || Number(date.slice(0, 4)) !== profile.taxYear || !memo.trim()) {
      toast('귀속 연도에 맞는 날짜와 거래 설명을 입력해주세요.');
      return;
    }
    if (actualAmount === null || (kind === 'in' && grossAmount === null)) {
      toast('금액은 0 이상의 원 단위 정수, 차감 세율은 0 이상 100 미만으로 입력해주세요.');
      return;
    }
    if (kind === 'in' && grossAmount < actualAmount) {
      toast('세전 수입은 실제 입금액 이상으로 입력해주세요.');
      return;
    }
    // 수입 계산에는 역산한 세전을, 현금 합계에는 실제 입금액을 사용한다.
    addRow({
      kind,
      m: Number(date.slice(5, 7)),
      date,
      cat,
      memo: memo.trim(),
      amt: kind === 'in' ? grossAmount : actualAmount,
      actualAmount,
      preTaxIncome: kind === 'in' ? grossAmount : null,
      deductionRate: kind === 'in' ? Number(deductionRate) : null,
      taxDeducted: kind === 'in' ? grossAmount - actualAmount : null,
      amountBasis: kind === 'in' ? 'GROSS' : 'CASH',
    });
    setMemo('');
    setAmt('');
    toast('내역을 추가했습니다.');
  };

  return (
    <Card title="수입·지출 내역 입력" icon="fileText" aside={<FileUploader compact />}>
      <form onSubmit={handleSubmit}>
        <div className="row">
          <div className="field" style={{ flex: '0 0 auto' }}>
            <span>구분</span>
            <Segmented label="구분" options={KINDS} value={kind} onChange={handleChangeKind} />
          </div>
          <TextField
            id="fDate"
            label="날짜"
            type="date"
            min={`${profile.taxYear}-01-01`}
            max={`${profile.taxYear}-12-31`}
            value={date}
            onChange={(event) => setDate(event.target.value)}
            required
          />
          <div className="field">
            <label htmlFor="fCat">카테고리</label>
            <select id="fCat" className="in" value={cat} onChange={(event) => setCat(event.target.value)}>
              {cats.map((category) => (
                <option key={category.value} value={category.value}>
                  {category.label}
                </option>
              ))}
            </select>
          </div>
        </div>
        <div className="row" style={{ marginTop: 10 }}>
          <TextField
            id="fMemo"
            label="거래 설명"
            placeholder="예: 영상 편집 프로그램 월 이용료"
            value={memo}
            onChange={(event) => setMemo(event.target.value)}
            className="wide"
            required
          />
        </div>
        <div className="row" style={{ marginTop: 10 }}>
          {kind === 'in' && (
            <TextField
              id="fDeductionRate"
              label="차감 세율 (%)"
              type="number"
              min="0"
              max="99.99"
              step="0.01"
              value={deductionRate}
              onChange={(event) => setDeductionRate(event.target.value)}
              required
            />
          )}
          <TextField
            id="fAmt"
            label={kind === 'in' ? '실제 입금액 (원)' : '실제 출금액 (원)'}
            inputMode="numeric"
            value={amt}
            onChange={(event) => setAmt(event.target.value)}
            required
          />
          <Button type="submit" variant="soft">
            + 내역 추가하기
          </Button>
        </div>
        {kind === 'in' && (
          <p className="small muted" role="status" style={{ marginTop: 10 }}>
            세전 수입 자동 계산:{' '}
            {preTaxIncome === null
              ? '입금액과 차감 세율을 입력해주세요.'
              : `${preTaxIncome.toLocaleString('ko-KR')}원 (원 단위 반올림)`}
            <br />
            차감 세율 기본값은 3.3%입니다. 실제 지급 내역과 다르면 수정하고, 차감 없이 받았다면 0%를 입력해주세요.
          </p>
        )}
      </form>
    </Card>
  );
}
