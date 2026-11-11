'use no memo';

import { useState } from 'react';
import styled from 'styled-components';

import { Button, Card, Segmented } from '../common/index';

import { INCOME_TRANSACTION_CATEGORIES, EXPENSE_TRANSACTION_CATEGORIES } from '../../data/transactionCategories';

import { useTax, useTaxStore } from '../../store/useTaxStore';

import { useToast } from '../../store/useToastStore';
import FileUploader from './FileUploader';

import { calculatePreTaxIncome, parseDeductedTaxRate, parseWon } from '../../utils/transactionAmount';

const UploadArea = styled.div({
  height: '220px',
  minHeight: '220px',
  overflow: 'auto',
  scrollbarGutter: 'stable',
  boxSizing: 'border-box',
  marginBottom: '16px',
  padding: '10px',
  border: '1px solid #e5eaf1',
  borderRadius: '10px',
  minWidth: 0,
});

const Form = styled.form({
  minWidth: 0,
  fieldset: {
    minWidth: 0,
    border: 0,
    padding: 0,
    margin: 0,
  },
  '.entry-row': {
    display: 'grid',
    gridTemplateColumns: '110px minmax(0, 1fr) minmax(0, 1fr)',
    gap: '12px',
    marginBottom: '12px',
    alignItems: 'end',
  },
  '.amount-row': {
    gridTemplateColumns: 'minmax(0, 1fr) minmax(0, 1fr) 128px',
  },
  'label, .kind-field': {
    display: 'grid',
    gap: '6px',
    minWidth: 0,
    fontSize: '13px',
  },
  'input, select': {
    width: '100%',
    minWidth: 0,
    height: '40px',
    boxSizing: 'border-box',
    border: '1px solid #dce3ed',
    borderRadius: '8px',
    padding: '0 10px',
    font: 'inherit',
    background: '#fff',
  },
  '.wide-field': { marginBottom: '12px' },
  '.amount-row button': {
    width: '128px',
    height: '40px',
    whiteSpace: 'nowrap',
  },
  '.calculation-note': {
    height: '64px',
    overflow: 'auto',
    margin: 0,
    fontSize: '12px',
    lineHeight: 1.6,
    color: '#536476',
  },
  '@media (max-width: 640px)': {
    '.entry-row, .amount-row': {
      gridTemplateColumns: 'minmax(0, 1fr)',
    },
    '.amount-row button': { width: '100%' },
  },
});

const KINDS = [
  { value: 'in', label: '수입' },
  { value: 'out', label: '지출', tone: 'dark' },
];

export function EntryForm() {
  const { addRow, profile } = useTax();
  const busy = useTaxStore((state) => state.taxSaveBusy === true);
  const toast = useToast();

  const [kind, setKind] = useState('in');
  const [date, setDate] = useState(() => new Date().toLocaleDateString('sv-SE'));
  const [cat, setCat] = useState(INCOME_TRANSACTION_CATEGORIES[0].value);
  const [memo, setMemo] = useState('');
  const [amt, setAmt] = useState('');
  const [deductionRate, setDeductionRate] = useState('3.3');

  const categories = kind === 'in' ? INCOME_TRANSACTION_CATEGORIES : EXPENSE_TRANSACTION_CATEGORIES;

  const preTaxIncome = calculatePreTaxIncome(parseWon(amt), parseDeductedTaxRate(deductionRate));

  function changeDeductionRate(event) {
    const value = event.target.value.trim().replace(/^0+(?=[0-9])/, '');
    const rate = parseDeductedTaxRate(value.replace(/\.$/, ''));

    // 소수점을 입력하는 도중에는 마지막 점을 남기고, 범위를 넘는 입력은 반영하지 않는다.
    if (value === '' || rate !== null) {
      setDeductionRate(value);
    }
  }

  function changeActualAmount(event) {
    const value = event.target.value;
    const amount = parseWon(value);

    // 입력과 붙여넣기에서 상한을 넘으면 이전 금액을 유지한다.
    if (value === '' || amount !== null) {
      setAmt(value === '' ? '' : String(amount));
    }
  }

  function changeKind(value) {
    if (busy) return;
    setKind(value);
    setCat((value === 'in' ? INCOME_TRANSACTION_CATEGORIES : EXPENSE_TRANSACTION_CATEGORIES)[0].value);
  }

  function submit(event) {
    event.preventDefault();

    if (useTaxStore.getState().taxSaveBusy) return;

    const actualAmount = parseWon(amt);
    const deductedTaxRate = parseDeductedTaxRate(deductionRate);
    const grossAmount = calculatePreTaxIncome(actualAmount, deductedTaxRate);

    if (!date || Number(date.slice(0, 4)) !== Number(profile.taxYear) || !memo.trim()) {
      toast('귀속 연도에 맞는 날짜와 거래 설명을 입력해주세요.');
      return;
    }

    if (actualAmount === null) {
      toast('실제 입출금액은 0~9,999,999,999,999원 사이의 정수로 입력해주세요.');
      return;
    }

    if (kind === 'in' && deductedTaxRate === null) {
      toast('차감 세율은 0~99.9999% 사이에서 소수점 이하 4자리까지 입력해주세요.');
      return;
    }

    if (kind === 'in' && grossAmount === null) {
      toast('역산한 세전 수입이 저장 범위를 초과합니다.');
      return;
    }

    if (kind === 'in' && grossAmount < actualAmount) {
      toast('세전 수입은 실제 입금액 이상으로 입력해주세요.');
      return;
    }

    addRow({
      kind,
      m: Number(date.slice(5, 7)),
      date,
      cat,
      memo: memo.trim(),
      amt: kind === 'in' ? grossAmount : actualAmount,
      actualAmount,
      preTaxIncome: kind === 'in' ? grossAmount : null,
      deductedTaxRate: kind === 'in' ? deductedTaxRate : null,
      taxDeducted: kind === 'in' ? grossAmount - actualAmount : null,
      amountBasis: kind === 'in' ? 'GROSS' : 'CASH',
    });

    setMemo('');
    setAmt('');
    toast('내역을 추가했습니다.');
  }

  return (
    <Card title="수입·지출 내역 입력" icon="fileText">
      <UploadArea aria-label="PDF 업로드와 분석 결과">
        <FileUploader compact />
      </UploadArea>

      <Form onSubmit={submit}>
        <fieldset disabled={busy}>
          <div className="entry-row">
            <div className="kind-field">
              <span>구분</span>
              <Segmented label="구분" options={KINDS} value={kind} onChange={changeKind} />
            </div>

            <label htmlFor="fDate">
              날짜
              <input
                id="fDate"
                type="date"
                min={`${profile.taxYear}-01-01`}
                max={`${profile.taxYear}-12-31`}
                value={date}
                onChange={(event) => setDate(event.target.value)}
                required
              />
            </label>

            <label htmlFor="fCat">
              카테고리
              <select id="fCat" value={cat} onChange={(event) => setCat(event.target.value)}>
                {categories.map((category) => (
                  <option key={category.value} value={category.value}>
                    {category.label}
                  </option>
                ))}
              </select>
            </label>
          </div>

          <label className="wide-field" htmlFor="fMemo">
            거래 설명
            <input
              id="fMemo"
              value={memo}
              maxLength={255}
              placeholder="예: 영상 편집 프로그램 월 이용료"
              onChange={(event) => setMemo(event.target.value)}
              required
            />
          </label>

          <div className="entry-row amount-row">
            <label
              htmlFor="fDeductionRate"
              style={{
                visibility: kind === 'in' ? 'visible' : 'hidden',
              }}
            >
              차감 세율 (%)
              <input
                id="fDeductionRate"
                inputMode="decimal"
                value={deductionRate}
                onChange={changeDeductionRate}
                disabled={busy || kind !== 'in'}
                required={kind === 'in'}
              />
            </label>

            <label htmlFor="fAmt">
              {kind === 'in' ? '실제 입금액 (원)' : '실제 출금액 (원)'}
              <input
                id="fAmt"
                inputMode="numeric"
                value={amt}
                onChange={changeActualAmount}
                required
              />
            </label>

            <Button type="submit" variant="soft" disabled={busy}>
              + 내역 추가하기
            </Button>
          </div>

          <p className="calculation-note" aria-live="polite">
            {kind === 'in' ? (
              <>
                세전 수입 자동 계산:{' '}
                {preTaxIncome === null
                  ? '입금액과 차감 세율을 입력해주세요.'
                  : `${preTaxIncome.toLocaleString('ko-KR')}원 (원 단위 반올림)`}
                <br />
                차감 세율은 실제 지급 내역에 맞춰 입력해주세요.
              </>
            ) : (
              '실제 출금액을 입력해주세요.'
            )}
          </p>
        </fieldset>
      </Form>
    </Card>
  );
}
