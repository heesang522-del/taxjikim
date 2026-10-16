// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { Button, Card, DataTable, Icon, Pill } from '../common/index';
import { useEffect, useRef, useState } from 'react';
import { INCOME_CATEGORIES, EXPENSE_CATEGORIES } from '../../data/options';
import { YEAR } from '../../data/taxConstants';
import { won } from '../../utils/format';
import { useTax, getPdfItemDefaults, getPdfItemPeriod } from '../../store/useTaxStore';
import {
  EVIDENCE_TYPES,
  EVIDENCE_STATUSES,
  saveReviewedTransactionApi,
  updateReviewedTransactionApi,
} from '../../api/taxApi';

export default function EntryTable() {
  const {
    rows,
    allRows,
    removeRow,
    inputMethod,
    setRowSelected,
    setAllRowsSelected,
    evidenceDocuments,
    removeUploadedFile,
    addReviewCandidate,
    setPdfDocumentReviewed,
  } = useTax();
  const sorted = [...allRows].sort((a, b) => b.m - a.m || (a.kind === 'in' ? -1 : 1));
  const columns = [
    {
      key: 'selected',
      header: '계산 포함',
      render: (row) => (
        <input
          type="checkbox"
          checked={row.selected !== false && !row.needsReview && !row.excluded}
          disabled={row.needsReview || row.excluded}
          aria-label={`${row.memo} 계산에 포함`}
          onChange={(event) => setRowSelected(row.id, event.target.checked)}
        />
      ),
    },
    {
      key: 'kind',
      header: '구분',
      render: (r) => (
        <Pill tone={r.kind === 'in' ? 'primary' : 'neutral'}>
          {r.kind === 'in' ? '수입' : r.kind === 'out' ? '지출' : '입력 필요'}
        </Pill>
      ),
    },
    {
      key: 'date',
      header: '날짜',
      num: true,
      render: (r) => r.date || (r.month ? `${r.month} · 월 집계` : r.year ? `${r.year}년 · 연간 집계` : '확인 필요'),
    },
    { key: 'cat', header: '카테고리', render: (r) => r.cat || '입력 필요' },
    { key: 'memo', header: '내용', render: (r) => r.memo || '입력 필요' },
    {
      key: 'amt',
      header: '금액',
      align: 'right',
      num: true,
      render: (r) => (r.amt === '' || r.amt == null ? '입력 필요' : won(r.amt)),
    },
    {
      key: 'reviewStatus',
      header: '상태',
      render: (row) =>
        row.excluded
          ? '제외됨'
          : row.needsReview
            ? '인식값 확인 필요'
            : row.autoImported
              ? '자동 입력 · 수정 가능'
              : '확인 완료',
    },
    {
      key: 'del',
      header: '',
      render: (r) => (
        <Button size="sm" variant="danger" className="icon" aria-label="삭제" onClick={() => removeRow(r.id)}>
          <Icon name="trash" size={16} />
        </Button>
      ),
    },
  ];
  return (
    <>
      <Card
        title={inputMethod === 'file' ? 'PDF에서 불러온 내역' : '직접 입력한 내역'}
        aside={
          <span className="muted small">
            {rows.length}/{allRows.length}건 선택
          </span>
        }
      >
        {allRows.length > 0 && (
          <div className="row" style={{ marginBottom: 10 }}>
            <Button size="sm" variant="ghost" onClick={() => setAllRowsSelected(true)}>
              전체 선택
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setAllRowsSelected(false)}>
              전체 해제
            </Button>
          </div>
        )}
        <p className="small muted" style={{ marginBottom: 10 }}>
          PDF에서 읽은 항목은 자동으로 목록에 표시합니다. 입력 필요 항목은 아래에서 채워 저장해 주세요. 확인한 내역 중
          체크한 항목만 계산에 포함합니다.
        </p>
        {!allRows.length && (
          <p className="state-msg">
            {inputMethod === 'file'
              ? evidenceDocuments.length
                ? '장부 거래는 없습니다. PDF 금액 검토는 아래에서 확인해 주세요.'
                : 'PDF에서 계산 가능한 장부 거래를 찾지 못했습니다.'
              : '직접 입력한 내역이 없습니다.'}
          </p>
        )}
        <DataTable columns={columns} rows={sorted} rowKey={(r) => r.id} />
      </Card>
      {inputMethod === 'file' && evidenceDocuments.length > 0 && (
        <Card title="PDF 금액 검토" aside={<span className="small muted">{evidenceDocuments.length}개 문서</span>}>
          <p className="small muted">
            ① 자동으로 읽은 내용을 확인하고 ② 잘못 읽힌 값만 수정한 뒤 ③ 계산에 포함할 항목과 사업 관련성을 최종 확인해
            주세요. 카드 사용액 자체가 사업 경비나 공제액으로 확정된 것은 아닙니다.
          </p>
          {evidenceDocuments.map((document) => (
            <section
              key={document.sourceFileKey}
              style={{ paddingBlock: 16, borderBottom: '1px solid var(--border-soft)', overflowWrap: 'anywhere' }}
            >
              <p style={{ fontWeight: 600 }}>{document.fileName}</p>
              <p className="small" style={{ marginTop: 6 }}>
                {EVIDENCE_TYPES[document.evidenceType] || '종류 확인 필요'} · 문서 귀속연도:{' '}
                {document.documentYear ?? '확인 필요'} · 선택 연도: {document.requestedTaxYear ?? '-'}
              </p>
              <p style={{ marginTop: 8 }} role="status">
                <Pill tone="warn">{EVIDENCE_STATUSES[document.status] || '분석 상태 확인 필요'}</Pill>
              </p>
              {document.status === 'YEAR_MISMATCH' && (
                <p className="small" style={{ marginTop: 8 }}>
                  문서 연도와 선택 연도가 다릅니다. 선택한 연도에 해당하는 거래인지 확인하고 날짜를 직접 입력해 주세요.
                </p>
              )}
              {(document.notices || []).map((notice, index) => (
                <p key={index} className="small muted" style={{ marginTop: 6 }}>
                  {notice}
                </p>
              ))}
              <EvidenceTables items={document.items} />
              <p className="small muted">
                개별 거래는 거래일, 월별 자료는 연월, 연간 자료는 귀속연도로 표시합니다. 연간·월별·상세 내역 중 한
                기준만 계산에 포함해 주세요. 전체 합계를 선택하면 같은 문서의 다른 금액은 자동 해제됩니다. 다른 금액을
                선택하면 전체 합계가 해제됩니다.
              </p>
              {document.items.map((item, index) => (
                <ReviewItem key={index} item={item} index={index} document={document} />
              ))}
              <div data-pdf-final-review style={{ paddingBlock: 12 }}>
                <label>
                  <input
                    type="checkbox"
                    checked={Boolean(document.reviewConfirmed)}
                    onChange={(event) => setPdfDocumentReviewed(document.sourceFileKey, event.target.checked)}
                  />{' '}
                  최종 확인: 원본과 금액·기간을 대조했고, 계산에 포함한 지출의 사업 관련성과 중복 여부를 확인했습니다.
                </label>
              </div>
              <Button size="sm" variant="soft" onClick={() => addReviewCandidate(document.sourceFileKey)}>
                누락 내역 직접 추가
              </Button>
              <Button size="sm" variant="ghost" onClick={() => removeUploadedFile(document.sourceFileKey)}>
                이 증빙을 목록에서 제거
              </Button>
            </section>
          ))}
        </Card>
      )}
    </>
  );
}

function ReviewItem({ item, index, document }) {
  const { confirmReviewItem, excludeReviewItem, fileRows, updatePdfDraft } = useTax();
  const savingRef = useRef(false);
  const [saving, setSaving] = useState(false);
  const previous = item.editedRow;
  const existing = fileRows.find(
    (row) => row.sourceFileKey === document.sourceFileKey && row.sourceItemIndex === index
  );
  // 날짜를 추측하지 않으며 수정된 값이 있으면 그 값을 편집 초기값으로 사용한다.
  const [kind, setKind] = useState(previous?.kind || getPdfItemDefaults(item).kind);
  const period = getPdfItemPeriod(item, document.documentYear);
  const monthly = Boolean(period.month);
  const annual =
    !period.date && !monthly && ['SUMMARY', 'CATEGORY_SUMMARY', 'VALIDATION_TOTAL'].includes(item.valueKind);
  const summaryPeriod = monthly || annual;
  const [date, setDate] = useState(
    previous?.year || previous?.month || previous?.date || period.date || period.month || period.year || ''
  );
  const [cat, setCat] = useState(previous?.cat || getPdfItemDefaults(item).category);
  const [memo, setMemo] = useState(
    previous?.memo ||
      item.description ||
      (['거래 금액(용도 확인 필요)', '기재 금액(용도 확인 필요)', '직접 추가한 내역'].includes(item.label)
        ? ''
        : item.label || '')
  );
  const [amount, setAmount] = useState(String(previous?.amt ?? item.reportedAmount ?? ''));
  const include = Boolean(existing?.selected);
  const setInclude = (selected) => updatePdfDraft(document.sourceFileKey, index, { selected });
  const [error, setError] = useState('');
  const [savedNotice, setSavedNotice] = useState('');
  const aggregate = ['SUMMARY', 'CATEGORY_SUMMARY', 'MONTHLY_SUMMARY', 'VALIDATION_TOTAL'].includes(item.valueKind);
  const cats = kind === 'in' ? INCOME_CATEGORIES : EXPENSE_CATEGORIES;
  useEffect(() => {
    // 날짜를 추측하지 않고 편집한 값을 현재 계산 목록에 전달한다.
    updatePdfDraft(document.sourceFileKey, index, {
      kind,
      date: summaryPeriod ? '' : date,
      month: monthly ? date : null,
      year: annual ? date : null,
      m: Number(date.slice(5, 7)),
      cat,
      memo,
      amt: amount === '' ? '' : Number(amount),
      selected: include,
    });
  }, [
    kind,
    date,
    monthly,
    annual,
    summaryPeriod,
    cat,
    memo,
    amount,
    include,
    document.sourceFileKey,
    index,
    updatePdfDraft,
  ]);
  const handleSubmit = async (event) => {
    event.preventDefault();
    if (savingRef.current) return;
    const value = Number(amount);
    const parsed = new Date(`${date}T00:00:00Z`);
    // 월별 집계는 원문에 있는 연월만 검증하고 임의의 거래일을 요구하지 않는다.
    const validPeriod = annual
      ? /^\d{4}$/.test(date) && Number(date) === YEAR
      : summaryPeriod
        ? /^\d{4}-(0[1-9]|1[0-2])$/.test(date) && Number(date.slice(0, 4)) === YEAR
        : /^\d{4}-\d{2}-\d{2}$/.test(date) &&
          !Number.isNaN(parsed.getTime()) &&
          parsed.toISOString().slice(0, 10) === date &&
          Number(date.slice(0, 4)) === YEAR;
    if (
      !['in', 'out'].includes(kind) ||
      !cats.includes(cat) ||
      !memo.trim() ||
      memo.trim().length > 200 ||
      !validPeriod ||
      !Number.isSafeInteger(value) ||
      (summaryPeriod ? value < 0 : value <= 0)
    ) {
      setError(`${YEAR}년의 정확한 기간, 구분, 카테고리, 내용, 양수 금액을 확인해 주세요.`);
      return;
    }
    savingRef.current = true;
    setSaving(true);
    setError('');
    setSavedNotice('');
    try {
      let row = {
        kind,
        date: summaryPeriod ? '' : date,
        month: monthly ? date : null,
        year: annual ? date : null,
        m: Number(date.slice(5, 7)),
        valueKind: item.valueKind,
        cat,
        memo: memo.trim(),
        amt: value,
        sourceFileId: document.fileId,
        persisted: false,
        selected: include,
      };
      // 월 집계는 실제 거래가 아니므로 날짜가 필수인 거래 저장 API로 전송하지 않는다.
      if (document.fileId && !summaryPeriod) {
        const persist = item.persisted ? updateReviewedTransactionApi : saveReviewedTransactionApi;
        const saved = await persist({
          fileId: document.fileId,
          itemIndex: index,
          taxYear: YEAR,
          transactionType: kind === 'in' ? 'INCOME' : 'EXPENSE',
          transactionDate: date,
          category: cat,
          description: memo.trim(),
          amount: value,
        });
        row = {
          ...row,
          kind: saved.transactionType === 'INCOME' ? 'in' : 'out',
          date: saved.transactionDate,
          m: Number(saved.transactionDate.slice(5, 7)),
          cat: saved.category,
          memo: saved.description,
          amt: Number(saved.amount),
          sourceFileId: saved.sourceFileId,
          transactionId: saved.transactionId,
          persisted: true,
        };
      }
      confirmReviewItem(document.sourceFileKey, index, row);
      setSavedNotice(
        summaryPeriod
          ? include
            ? '월별 금액을 현재 계산에 반영했습니다. 개별 거래로 DB 저장하지 않습니다.'
            : '월별 수정값을 현재 화면에 반영했습니다. 계산에서는 제외됩니다.'
          : include
            ? '저장하고 계산에 포함했습니다.'
            : '저장했습니다. 현재 계산에서는 제외됩니다.'
      );
    } catch (failure) {
      setError(failure.message);
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  };
  return (
    <form
      onSubmit={handleSubmit}
      data-pdf-review-form
      style={{ marginBlock: 16, padding: 12, border: '1px solid var(--border-soft)', borderRadius: 8 }}
    >
      <p className="small">
        {item.label} · {item.page ? `${item.page}페이지` : '직접 입력'}
        {item.transactionMonth ? ` · ${item.transactionMonth}` : ''}
        {aggregate ? ' · 집계 금액' : ''}
      </p>
      {!item.category && getPdfItemDefaults(item).category && (
        <p className="small muted">카테고리는 자동 추천값입니다. 실제 용도에 맞게 수정해 주세요.</p>
      )}
      {summaryPeriod && (
        <p className="small muted">
          집계 자료는 기재 기간 그대로 현재 화면의 계산에 반영합니다. 실제 거래일이 필요한 개별 거래 DB에는 저장하지
          않습니다.
        </p>
      )}
      {!item.transactionDate && !summaryPeriod && (
        <p className="small muted">
          문서에 개별 거래일이 없습니다{item.transactionMonth ? ` (기재 기간: ${item.transactionMonth})` : ''}. 계산에
          사용할 실제 날짜를 입력해 주세요.
        </p>
      )}
      {item.excluded ? (
        <>
          <p className="small muted">현재 검토·계산에서 제외했습니다. 서버 기록은 유지됩니다.</p>
          <Button
            size="sm"
            variant="ghost"
            type="button"
            onClick={() => excludeReviewItem(document.sourceFileKey, index, false)}
          >
            다시 검토
          </Button>
        </>
      ) : (
        <>
          <fieldset disabled={saving} style={{ border: 0, padding: 0, margin: 0 }}>
            <div className="row">
              <select
                aria-label="검토 항목 구분"
                required
                value={kind}
                onChange={(event) => {
                  setKind(event.target.value);
                  setCat('');
                }}
              >
                <option value="">구분 선택</option>
                <option value="in">수입</option>
                <option value="out">지출</option>
              </select>
              <input
                aria-label={annual ? '검토 항목 귀속연도' : monthly ? '검토 항목 월' : '검토 항목 날짜'}
                required
                type={annual ? 'number' : monthly ? 'month' : 'date'}
                value={date}
                onChange={(event) => setDate(event.target.value)}
              />
              <select
                aria-label="검토 항목 카테고리"
                required
                value={cat}
                onChange={(event) => setCat(event.target.value)}
              >
                <option value="">카테고리 선택</option>
                {cats.map((value) => (
                  <option key={value}>{value}</option>
                ))}
              </select>
              <input
                aria-label="검토 항목 내용"
                required
                maxLength={200}
                placeholder="거래 내용 직접 입력"
                value={memo}
                onChange={(event) => setMemo(event.target.value)}
              />
              <input
                aria-label="검토 항목 금액"
                required
                type="number"
                min={summaryPeriod ? '0' : '1'}
                step="1"
                value={amount}
                onChange={(event) => setAmount(event.target.value)}
              />
            </div>
            {aggregate && (
              <p className="small muted">월별·연간 합계와 그 안의 상세 내역을 함께 계산하지 않도록 확인해 주세요.</p>
            )}
            <label className="small" style={{ display: 'block', marginTop: 8 }}>
              <input type="checkbox" checked={include} onChange={(event) => setInclude(event.target.checked)} /> 계산에
              포함
            </label>
            <p className="small muted">
              현재 계산 상태: {existing?.selected ? '포함' : '제외'} · 입력·선택 변경은 현재 계산에 바로 반영됩니다.
            </p>
            <Button type="submit" size="sm">
              {saving
                ? '저장 중...'
                : summaryPeriod
                  ? '확인 후 계산 목록에 반영'
                  : item.confirmed
                    ? '수정 저장'
                    : '확인 후 저장'}
            </Button>
            <Button
              type="button"
              size="sm"
              variant="ghost"
              onClick={() => excludeReviewItem(document.sourceFileKey, index, true)}
            >
              이 항목 제외
            </Button>
          </fieldset>
        </>
      )}
      {error && (
        <p role="alert" className="small">
          {error}
        </p>
      )}
      {savedNotice && (
        <p role="status" className="small">
          {savedNotice}
        </p>
      )}
    </form>
  );
}

function EvidenceTables({ items }) {
  const summary = items.filter((item) => item.valueKind !== 'MONTHLY_SUMMARY');
  const monthly = items.filter((item) => item.valueKind === 'MONTHLY_SUMMARY');

  const columns = [
    { key: 'label', header: '추출 항목' },
    {
      key: 'period',
      header: '기간',
      render: (item) => item.transactionMonth || item.transactionDate || '거래일 없음 · 집계',
    },
    { key: 'page', header: '원본', render: (item) => `${item.page}페이지` },
    {
      key: 'reportedAmount',
      header: '기재금액',
      align: 'right',
      render: (item) => {
        const amount = Number(item.reportedAmount);
        return Number.isSafeInteger(amount) && amount >= 0 ? won(amount) : '금액 확인 필요';
      },
    },
    {
      key: 'review',
      header: '상태',
      render: (item) =>
        item.excluded
          ? '사용자 제외'
          : item.confirmed
            ? item.persisted
              ? 'DB 저장 완료'
              : '검토 저장됨'
            : item.valueKind === 'VALIDATION_TOTAL'
              ? '전체 합계 · 단독 선택'
              : '원본 대조 필요',
    },
  ];
  if (!items.length) return <p className="state-msg">표에서 금액을 추출하지 못했습니다. 원본을 확인해 주세요.</p>;
  return (
    <>
      {summary.length > 0 && (
        <>
          <p style={{ marginTop: 12, fontWeight: 600 }}>집계 및 상세 금액</p>
          <DataTable columns={columns} rows={summary} />
        </>
      )}
      {monthly.length > 0 && (
        <>
          <p style={{ marginTop: 16, fontWeight: 600 }}>월별 사용금액</p>
          <p className="small muted">
            0원도 표시합니다. 아래 입력칸에서 수정할 수 있으며 집계와 상세 내역의 중복 여부를 확인해 주세요.
          </p>
          {monthly.length ? <DataTable columns={columns} rows={monthly} /> : null}
        </>
      )}
    </>
  );
}
