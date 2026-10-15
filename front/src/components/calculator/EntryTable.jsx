// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { Button, Card, DataTable, Icon, Pill } from '../common/index';
import { useRef, useState } from 'react';
import { useAuth } from '../../store/useAuthStore';
import { INCOME_CATEGORIES, EXPENSE_CATEGORIES } from '../../data/options';
import { YEAR } from '../../data/taxConstants';
import { won } from '../../utils/format';
import { useTax } from '../../store/useTaxStore';
import { EVIDENCE_TYPES, EVIDENCE_STATUSES, saveReviewedTransactionApi } from '../../api/taxApi';

export default function EntryTable() {
  const { rows, allRows, removeRow, inputMethod, setRowSelected, setAllRowsSelected, evidenceDocuments, removeUploadedFile } = useTax();
  const sorted = [...allRows].sort((a, b) => b.m - a.m || (a.kind === 'in' ? -1 : 1));
  const columns = [
    { key: 'selected', header: '계산 포함', render: (row) => <input type="checkbox" checked={row.selected !== false} aria-label={`${row.memo} 계산에 포함`} onChange={(event) => setRowSelected(row.id, event.target.checked)} /> },
    { key: 'kind', header: '구분', render: (r) => <Pill tone={r.kind === 'in' ? 'primary' : 'neutral'}>{r.kind === 'in' ? '수입' : '지출'}</Pill> },
    { key: 'date', header: '날짜', num: true, render: (r) => r.date.slice(5) },
    { key: 'cat', header: '카테고리' },
    { key: 'memo', header: '내용' },
    { key: 'amt', header: '금액', align: 'right', num: true, render: (r) => won(r.amt) },
    { key: 'del', header: '', render: (r) => <Button size="sm" variant="danger" className="icon" aria-label="삭제" onClick={() => removeRow(r.id)}><Icon name="trash" size={16} /></Button> },
  ];
  return (<>
    <Card title={inputMethod === 'file' ? 'PDF에서 불러온 내역' : '직접 입력한 내역'} aside={<span className="muted small">{rows.length}/{allRows.length}건 선택</span>}>
      {allRows.length > 0 && <div className="row" style={{ marginBottom: 10 }}>
        <Button size="sm" variant="ghost" onClick={() => setAllRowsSelected(true)}>전체 선택</Button>
        <Button size="sm" variant="ghost" onClick={() => setAllRowsSelected(false)}>전체 해제</Button>
      </div>}
      <p className="small muted" style={{ marginBottom: 10 }}>체크한 내역만 합계와 계산에 포함합니다. 휴지통은 현재 계산 목록에서 내역을 제거합니다.</p>
      {!allRows.length && <p className="state-msg">{inputMethod === 'file'
        ? evidenceDocuments.length ? '장부 거래는 없습니다. PDF 금액 검토는 아래에서 확인해 주세요.' : 'PDF에서 계산 가능한 장부 거래를 찾지 못했습니다.'
        : '직접 입력한 내역이 없습니다.'}</p>}
      <DataTable columns={columns} rows={sorted} rowKey={(r) => r.id} />
    </Card>
    {inputMethod === 'file' && evidenceDocuments.length > 0 && <Card title="PDF 금액 검토" aside={<span className="small muted">{evidenceDocuments.length}개 문서</span>}>
      <p className="small muted">아래 금액은 증빙에 적힌 금액 후보입니다. 사업 경비·공제액·계산된 세액이 아니며 현재 거래 계산에 포함되지 않습니다.</p>
      {evidenceDocuments.map((document) => <section key={document.sourceFileKey} style={{ paddingBlock: 16, borderBottom: '1px solid var(--border-soft)', overflowWrap: 'anywhere' }}>
        <p style={{ fontWeight: 600 }}>{document.fileName}</p>
        <p className="small" style={{ marginTop: 6 }}>
          {EVIDENCE_TYPES[document.evidenceType] || '종류 확인 필요'} · 문서 귀속연도: {document.documentYear ?? '확인 필요'} · 선택 연도: {document.requestedTaxYear ?? '-'}
        </p>
        <p style={{ marginTop: 8 }} role="status"><Pill tone="warn">{EVIDENCE_STATUSES[document.status] || '분석 상태 확인 필요'}</Pill></p>
        {document.status === 'YEAR_MISMATCH' && <p className="small" style={{ marginTop: 8 }}>문서 연도와 선택 연도가 다릅니다. 이 결과는 계산에 사용할 수 없습니다.</p>}
        {(document.notices || []).map((notice, index) => <p key={index} className="small muted" style={{ marginTop: 6 }}>{notice}</p>)}
        <EvidenceTables items={document.items} />
        <p className="small muted">집계·월별 사용금액은 조회용이며 거래일을 임의로 만들지 않습니다. 실제 거래일이 있는 양수 상세 항목만 아래에서 확인 후 추가할 수 있습니다. 회원 파일은 DB 저장 후 반영되며 목록 제거는 서버 기록을 삭제하지 않습니다.</p>
        {document.items.map((item, index) => !item.confirmed && item.valueKind === 'DETAIL' && item.transactionDate && Number(item.reportedAmount) > 0 && <ReviewItem key={index} item={item} index={index} document={document} />)}
        <Button size="sm" variant="ghost" onClick={() => removeUploadedFile(document.sourceFileKey)}>이 증빙을 목록에서 제거</Button>
      </section>)}
    </Card>}
  </>
  );
}

function ReviewItem({ item, index, document }) {
  const { confirmReviewItem } = useTax();
  const { accessToken } = useAuth();
  const savingRef = useRef(false);
  const [saving, setSaving] = useState(false);
  // 백엔드가 명확히 인식한 구분은 기본 선택하고 사용자가 수정할 수 있게 한다.
  const [kind, setKind] = useState(item.transactionType === 'EXPENSE' ? 'out' : item.transactionType === 'INCOME' ? 'in' : '');
  const [date, setDate] = useState(item.transactionDate || '');
  const [cat, setCat] = useState('');
  const [memo, setMemo] = useState('');
  const [amount, setAmount] = useState(String(item.reportedAmount));
  const [error, setError] = useState('');
  const cats = kind === 'in' ? INCOME_CATEGORIES : EXPENSE_CATEGORIES;
  const submit = async (event) => {
    event.preventDefault();
    if (savingRef.current) return;
    const value = Number(amount);
    const parsed = new Date(`${date}T00:00:00Z`);
    if (!['in', 'out'].includes(kind) || !cats.includes(cat) || !memo.trim()
      || !/^\d{4}-\d{2}-\d{2}$/.test(date) || Number.isNaN(parsed.getTime())
      || parsed.toISOString().slice(0, 10) !== date || Number(date.slice(0, 4)) !== YEAR
      || !Number.isSafeInteger(value) || value <= 0) {
      setError(`${YEAR}년의 정확한 날짜, 구분, 카테고리, 내용, 양수 금액을 확인해 주세요.`); return;
    }
    savingRef.current = true; setSaving(true); setError('');
    try {
      let row = { kind, date, m: Number(date.slice(5, 7)), cat, memo: memo.trim(), amt: value, sourceFileId: document.fileId, persisted: false };
      if (document.fileId) {
        if (!accessToken) throw new Error('서버 저장을 위해 다시 로그인해 주세요.');
        const saved = await saveReviewedTransactionApi({ fileId: document.fileId, itemIndex: index, taxYear: YEAR,
          transactionType: kind === 'in' ? 'INCOME' : 'EXPENSE', transactionDate: date,
          category: cat, description: memo.trim(), amount: value }, accessToken);
        // 재시도 응답에는 이미 저장된 값이 올 수 있으므로 서버 값을 기준으로 반영한다.
        row = { kind: saved.transactionType === 'INCOME' ? 'in' : 'out', date: saved.transactionDate,
          m: Number(saved.transactionDate.slice(5, 7)), cat: saved.category, memo: saved.description,
          amt: Number(saved.amount), sourceFileId: saved.sourceFileId, transactionId: saved.transactionId, persisted: true };
      }
      confirmReviewItem(document.sourceFileKey, index, row);
    } catch (failure) { setError(failure.message); }
    finally { savingRef.current = false; setSaving(false); }
  };
  return <form onSubmit={submit} style={{ marginBlock: 12 }}>
    <p className="small">{item.label} · {item.page}페이지</p>
    <div className="row">
      <select aria-label="검토 항목 구분" required value={kind} onChange={(event) => { setKind(event.target.value); setCat(''); }}>
        <option value="">구분 선택</option><option value="in">수입</option><option value="out">지출</option>
      </select>
      <input aria-label="검토 항목 날짜" required type="date" value={date} onChange={(event) => setDate(event.target.value)} />
      <select aria-label="검토 항목 카테고리" required value={cat} onChange={(event) => setCat(event.target.value)}>
        <option value="">카테고리 선택</option>{cats.map((value) => <option key={value}>{value}</option>)}
      </select>
      <input aria-label="검토 항목 내용" required placeholder="거래 내용 직접 입력" value={memo} onChange={(event) => setMemo(event.target.value)} />
      <input aria-label="검토 항목 금액" required type="number" min="1" step="1" value={amount} onChange={(event) => setAmount(event.target.value)} />
      <Button type="submit" size="sm" disabled={saving}>{saving ? '저장 중...' : document.fileId ? '저장 후 계산에 추가' : '확인 후 계산에 추가'}</Button>
    </div>
    {error && <p role="alert" className="small">{error}</p>}
  </form>;
}

function EvidenceTables({ items }) {
  const summary = items.filter((item) => item.valueKind !== 'MONTHLY_SUMMARY');
  const monthly = items.filter((item) => item.valueKind === 'MONTHLY_SUMMARY');
  const monthlyNonzero = monthly.filter((item) => Number(item.reportedAmount) > 0);
  const columns = [
    { key: 'label', header: '추출 항목' },
    { key: 'period', header: '기간', render: (item) => item.transactionMonth || item.transactionDate || '거래일 없음 · 집계' },
    { key: 'page', header: '원본', render: (item) => `${item.page}페이지` },
    { key: 'reportedAmount', header: '기재금액', align: 'right', render: (item) => {
      const amount = Number(item.reportedAmount);
      return Number.isSafeInteger(amount) && amount >= 0 ? won(amount) : '금액 확인 필요';
    } },
    { key: 'review', header: '상태', render: (item) => item.confirmed ? item.persisted ? 'DB 저장 완료' : '계산에 추가됨'
      : item.valueKind === 'VALIDATION_TOTAL' ? '검증용 · 계산 제외' : '원본 대조 필요' },
  ];
  if (!items.length) return <p className="state-msg">표에서 금액을 추출하지 못했습니다. 원본을 확인해 주세요.</p>;
  return <>
    {summary.length > 0 && <><p style={{ marginTop: 12, fontWeight: 600 }}>집계 및 상세 금액</p><DataTable columns={columns} rows={summary} /></>}
    {monthly.length > 0 && <>
      <p style={{ marginTop: 16, fontWeight: 600 }}>월별 사용금액</p>
      <p className="small muted">0원인 월은 숨겼습니다. 월별 집계이므로 거래일은 없으며 위 집계와 중복 합산하지 않습니다.</p>
      {monthlyNonzero.length ? <DataTable columns={columns} rows={monthlyNonzero} /> : <p className="small muted">월별 금액이 모두 0원입니다.</p>}
    </>}
  </>;
}
