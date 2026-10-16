// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { create } from 'zustand';
import { sampleRows } from '../data/sampleRows';
import { YEAR } from '../data/taxConstants';

// 문서에 없는 날짜는 만들지 않고 구분·카테고리는 수정 가능한 추천값으로 채운다.
export function getPdfItemDefaults(item) {
  const label = item.description || item.label || '';
  const expense = /사용금액|사용액|지출|납입|납부|의료비/.test(label);
  const kind = item.transactionType === 'INCOME' ? 'in' : item.transactionType === 'EXPENSE' || expense ? 'out' : '';
  const category =
    item.category ||
    (kind === 'out' ? (/교통|출장/.test(label) ? '교통·출장' : '기타 경비') : kind === 'in' ? '기타 수입' : '');
  return { kind, category };
}

// 거래일·월·연도는 원문에 기재된 정밀도 그대로 유지한다.
export function getPdfItemPeriod(item, year) {
  if (item.transactionDate) return { date: item.transactionDate, month: null, year: null };
  if (item.transactionMonth) return { date: '', month: item.transactionMonth, year: null };
  return {
    date: '',
    month: null,
    year: ['SUMMARY', 'CATEGORY_SUMMARY', 'VALIDATION_TOTAL'].includes(item.valueKind) && year ? String(year) : null,
  };
}

// 최종 확인과 중복 검사는 계산 시작 전에 한 번 수행한다.
export function getPdfCalculationIssue(state, requireFinalConfirmation = true) {
  const rows = getSelectedTaxRows(state);
  for (const document of state.evidenceDocuments) {
    const selected = rows.filter((row) => row.sourceFileKey === document.sourceFileKey);
    if (!selected.length) continue;
    if (selected.length > 1 && selected.some((row) => row.valueKind === 'VALIDATION_TOTAL'))
      return `${document.fileName}: 전체 합계와 다른 금액을 함께 계산할 수 없습니다.`;
    const levels = new Set(selected.map((row) => (row.year ? 'year' : row.month ? 'month' : 'detail')));
    if (levels.size > 1)
      return `${document.fileName}: 연간·월별·상세 금액 중 한 기준만 선택해 주세요. 중복 합산될 수 있습니다.`;
    if (requireFinalConfirmation && !document.reviewConfirmed)
      return `${document.fileName}: 원본과 선택 금액의 사업 관련성을 확인한 뒤 최종 확인을 선택해 주세요.`;
  }
  return '';
}

// 같은 문서에서는 전체 합계와 나머지 금액을 동시에 선택하지 않는다.
function selectPdfRow(rows, id, selected) {
  const target = rows.find((row) => row.id === id);
  if (!target) return rows;
  const enabled = selected && !target.needsReview && !target.excluded;
  const total = target.valueKind === 'VALIDATION_TOTAL';
  return rows.map((row) => {
    if (row.id === id) return { ...row, selected: enabled };
    if (enabled && row.sourceFileKey === target.sourceFileKey && (total || row.valueKind === 'VALIDATION_TOTAL'))
      return { ...row, selected: false };
    return row;
  });
}

// 전체 선택도 문서별 한 집계 기준을 유지한다. 사용자가 선택한 전체 합계는 유지한다.
function selectPdfRows(rows, selected, fileKey) {
  const keys = new Set(rows.filter((row) => !fileKey || row.sourceFileKey === fileKey).map((row) => row.sourceFileKey));
  let result = rows;
  for (const key of keys) {
    const candidates = rows.filter((row) => row.sourceFileKey === key && !row.needsReview && !row.excluded);
    const total = candidates.find((row) => row.valueKind === 'VALIDATION_TOTAL' && row.selected);
    const preferred = total
      ? [total]
      : candidates.some((row) => row.month)
        ? candidates.filter((row) => row.month && row.valueKind !== 'VALIDATION_TOTAL')
        : candidates.some((row) => row.date)
          ? candidates.filter((row) => row.date && row.valueKind !== 'VALIDATION_TOTAL')
          : candidates.some((row) => row.valueKind !== 'VALIDATION_TOTAL')
            ? candidates.filter((row) => row.valueKind !== 'VALIDATION_TOTAL')
            : candidates.slice(0, 1);
    const ids = new Set(preferred.map((row) => row.id));
    result = result.map((row) =>
      row.sourceFileKey === key ? { ...row, selected: Boolean(selected && ids.has(row.id)) } : row
    );
  }
  return result;
}

// 계산에 필요한 값만 확인한다. DB 저장 여부는 계산 가능 여부와 분리한다.
export function isPdfRowReady(row) {
  const period = row.date || row.month || row.year || '';
  const parsed = new Date(`${period}T00:00:00Z`);
  const validPeriod = row.date
    ? /^\d{4}-\d{2}-\d{2}$/.test(period) &&
      !Number.isNaN(parsed.getTime()) &&
      parsed.toISOString().slice(0, 10) === period
    : row.month
      ? /^\d{4}-(0[1-9]|1[0-2])$/.test(period)
      : /^\d{4}$/.test(period);
  return (
    validPeriod &&
    Number(period.slice(0, 4)) === YEAR &&
    ['in', 'out'].includes(row.kind) &&
    row.amt !== '' &&
    Number.isSafeInteger(Number(row.amt)) &&
    Number(row.amt) >= 0
  );
}

export const EMPTY_SIM = { extraExp: 0, extraInc: 0, yellow: 0 };

export const getAllTaxRows = (state) => (state.inputMethod === 'file' ? state.fileRows : state.manualRows);
export const getSelectedTaxRows = (state) =>
  getAllTaxRows(state).filter((row) => row.selected !== false && !row.needsReview && !row.excluded);

// 세금 계산 화면에서 입력하는 수입/지출 내역, 프로필, 시뮬레이션 값
export const useTaxStore = create((set, get) => ({
  inputMethod: 'manual',
  manualRows: sampleRows,
  fileRows: [],
  uploadedFiles: [],
  evidenceDocuments: [],
  pendingRows: [],
  profile: { type: '프리랜서(3.3% 원천징수)', auto33: true },
  sim: EMPTY_SIM,
  calculated: false,

  // 내역이 바뀌면 이전 계산 결과는 더 이상 맞지 않으므로 calculated를 되돌린다
  // 여러 PDF에서 같은 순간에 추가된 거래도 서로 다른 ID를 갖도록 한다.
  addRow: (row) =>
    set((s) => ({ manualRows: [...s.manualRows, { ...row, id: `n${crypto.randomUUID()}` }], calculated: false })),
  // 파일 내역과 직접 입력은 분리 보관하고 선택된 방식의 내역만 계산에 사용한다.
  setInputMethod: (method) => {
    if (!['manual', 'file'].includes(method) || method === get().inputMethod) return;
    set({ inputMethod: method, calculated: false, sim: EMPTY_SIM });
  },
  addFileResult: (rows, file) =>
    set((state) => ({
      fileRows: [
        ...state.fileRows,
        ...rows.map((row) => ({ ...row, sourceFileKey: file.key, selected: true, id: `f${crypto.randomUUID()}` })),
      ],
      uploadedFiles: [...state.uploadedFiles, file],
      calculated: false,
    })),
  // 추출 후보는 업로드 직후 목록에 표시하되 사용자 확인 전에는 계산에서 제외한다.
  addEvidenceResult: (evidence, file) =>
    set((state) => ({
      fileRows: [
        ...state.fileRows,
        ...evidence.items.map((item, index) => {
          const hasMonthly = evidence.items.some((value) => value.valueKind === 'MONTHLY_SUMMARY');
          // 월 집계와 연간 합계를 동시에 더하지 않는다. 월 집계가 없으면 개별 거래를 우선한다.
          const hasDetails = evidence.items.some((value) => value.valueKind === 'DETAIL');
          const preferred = hasMonthly
            ? item.valueKind === 'MONTHLY_SUMMARY'
            : hasDetails
              ? item.valueKind === 'DETAIL'
              : ['SUMMARY', 'CATEGORY_SUMMARY'].includes(item.valueKind);
          const row = {
            id: `f${crypto.randomUUID()}`,
            sourceFileKey: file.key,
            sourceFileId: file.fileId,
            sourceItemIndex: index,
            kind: getPdfItemDefaults(item).kind,
            ...getPdfItemPeriod(item, evidence.documentYear),
            valueKind: item.valueKind,
            m:
              item.transactionDate || item.transactionMonth
                ? Number((item.transactionDate || item.transactionMonth).slice(5, 7))
                : null,
            cat: getPdfItemDefaults(item).category,
            memo: item.description || item.label || '',
            amt: item.reportedAmount ?? '',
            persisted: false,
            autoImported: true,
          };
          const ready = isPdfRowReady(row);
          return { ...row, needsReview: !ready, selected: preferred && ready };
        }),
      ],
      evidenceDocuments: [
        ...state.evidenceDocuments,
        { ...evidence, sourceFileKey: file.key, fileId: file.fileId, fileName: file.name, reviewConfirmed: false },
      ],
      uploadedFiles: state.uploadedFiles.some((entry) => entry.key === file.key)
        ? state.uploadedFiles
        : [...state.uploadedFiles, file],
    })),
  // 편집 중인 값을 현재 계산 목록에 즉시 반영한다. 서버 저장은 명시적 저장 버튼만 수행한다.
  setPdfDocumentReviewed: (key, reviewed) =>
    set((state) => ({
      evidenceDocuments: state.evidenceDocuments.map((entry) =>
        entry.sourceFileKey === key ? { ...entry, reviewConfirmed: reviewed } : entry
      ),
      calculated: false,
    })),
  updatePdfDraft: (key, index, patch) =>
    set((state) => ({
      evidenceDocuments: state.evidenceDocuments.map((entry) =>
        entry.sourceFileKey === key ? { ...entry, reviewConfirmed: false } : entry
      ),
      fileRows: (() => {
        const target = state.fileRows.find((row) => row.sourceFileKey === key && row.sourceItemIndex === index);
        if (!target) return state.fileRows;
        const updated = { ...target, ...patch };
        updated.needsReview = !isPdfRowReady(updated);
        const rows = state.fileRows.map((row) => (row.id === target.id ? updated : row));
        return selectPdfRow(rows, target.id, Boolean(patch.selected));
      })(),
      calculated: false,
    })),
  // 원본 순서와 항목 인덱스는 유지해 저장된 거래의 연결이 바뀌지 않게 한다.
  addReviewCandidate: (key) =>
    set((state) => ({
      evidenceDocuments: state.evidenceDocuments.map((entry) =>
        entry.sourceFileKey === key
          ? {
              ...entry,
              items: [
                ...entry.items,
                { label: '직접 추가한 내역', reportedAmount: '', page: null, valueKind: 'MANUAL', confirmed: false },
              ],
            }
          : entry
      ),
    })),
  excludeReviewItem: (key, index, excluded) =>
    set((state) => ({
      evidenceDocuments: state.evidenceDocuments.map((entry) =>
        entry.sourceFileKey === key
          ? {
              ...entry,
              reviewConfirmed: false,
              items: entry.items.map((value, i) => (i === index ? { ...value, excluded } : value)),
            }
          : entry
      ),
      fileRows: state.fileRows.map((row) =>
        row.sourceFileKey === key && row.sourceItemIndex === index ? { ...row, selected: false, excluded } : row
      ),
      calculated: false,
    })),
  // 회원은 API 성공 후 갱신한다. 재수정은 기존 행을 교체하고 중복 추가하지 않는다.
  confirmReviewItem: (key, index, row) =>
    set((state) => {
      const item = state.evidenceDocuments.find((entry) => entry.sourceFileKey === key)?.items[index];
      if (!item) return {};
      const existing = state.fileRows.find((value) => value.sourceFileKey === key && value.sourceItemIndex === index);
      const updated = {
        ...row,
        needsReview: false,
        excluded: false,
        sourceFileKey: key,
        sourceItemIndex: index,
        id: existing?.id || `f${crypto.randomUUID()}`,
      };
      return {
        fileRows: selectPdfRow(
          existing
            ? state.fileRows.map((value) => (value.id === existing.id ? updated : value))
            : [...state.fileRows, updated],
          updated.id,
          Boolean(updated.selected)
        ),
        evidenceDocuments: state.evidenceDocuments.map((entry) =>
          entry.sourceFileKey === key
            ? {
                ...entry,
                reviewConfirmed: false,
                items: entry.items.map((value, i) =>
                  i === index
                    ? {
                        ...value,
                        confirmed: true,
                        excluded: false,
                        persisted: row.persisted,
                        transactionId: row.transactionId,
                        editedRow: row,
                      }
                    : value
                ),
              }
            : entry
        ),
        calculated: false,
      };
    }),
  removeRow: (id) =>
    set((state) => {
      const key = state.inputMethod === 'file' ? 'fileRows' : 'manualRows';
      return { [key]: state[key].filter((row) => row.id !== id), calculated: false };
    }),
  // 원본·DB 기록은 유지하고 현재 계산에 포함할 행만 선택하거나 목록에서 제거한다.
  setRowSelected: (id, selected) =>
    set((state) => {
      const key = state.inputMethod === 'file' ? 'fileRows' : 'manualRows';
      return {
        evidenceDocuments: state.evidenceDocuments.map((entry) => ({ ...entry, reviewConfirmed: false })),
        [key]:
          key === 'fileRows'
            ? selectPdfRow(state.fileRows, id, selected)
            : state[key].map((row) => (row.id === id ? { ...row, selected } : row)),
        calculated: false,
      };
    }),
  setAllRowsSelected: (selected) =>
    set((state) => {
      const key = state.inputMethod === 'file' ? 'fileRows' : 'manualRows';
      return {
        evidenceDocuments: state.evidenceDocuments.map((entry) => ({ ...entry, reviewConfirmed: false })),
        [key]:
          key === 'fileRows'
            ? selectPdfRows(state.fileRows, selected)
            : state[key].map((row) => ({ ...row, selected })),
        calculated: false,
      };
    }),
  setFileSelected: (key, selected) =>
    set((state) => ({
      evidenceDocuments: state.evidenceDocuments.map((entry) =>
        entry.sourceFileKey === key ? { ...entry, reviewConfirmed: false } : entry
      ),
      fileRows: selectPdfRows(state.fileRows, selected, key),
      calculated: false,
    })),
  removeUploadedFile: (key) =>
    set((state) => ({
      uploadedFiles: state.uploadedFiles.filter((file) => file.key !== key),
      fileRows: state.fileRows.filter((row) => row.sourceFileKey !== key),
      evidenceDocuments: state.evidenceDocuments.filter((document) => document.sourceFileKey !== key),
      calculated: false,
    })),
  /** CSV 업로드에서 구분·카테고리·날짜·금액을 확실히 읽지 못한 내역을 따로 쌓아 둔다 */
  addPendingRows: (items) => set((s) => ({ pendingRows: [...s.pendingRows, ...items] })),
  /** 확인이 필요한 내역을 사용자가 보정한 값으로 실제 내역에 추가한다 */
  resolvePendingRow: (id, patch) => {
    const row = get().pendingRows.find((r) => r.id === id);
    if (!row) return;
    const merged = { ...row, ...patch };
    get().addRow({
      kind: merged.kind,
      date: merged.date,
      m: Math.min(12, Number(merged.date.slice(5, 7))),
      cat: merged.cat,
      memo: merged.memo,
      amt: merged.amt,
    });
    set((s) => ({ pendingRows: s.pendingRows.filter((r) => r.id !== id) }));
  },
  discardPendingRow: (id) => set((s) => ({ pendingRows: s.pendingRows.filter((r) => r.id !== id) })),
  updateProfile: (patch) => set((s) => ({ profile: { ...s.profile, ...patch } })),
  patchSim: (patch) => set((s) => ({ sim: { ...s.sim, ...patch } })),
  resetSim: () => set({ sim: EMPTY_SIM }),
  setCalculated: (value) => set({ calculated: value }),
}));

export const useTax = () => {
  const state = useTaxStore();
  // 집계·결과·시뮬레이션은 공통 진입점에서 선택된 방식의 데이터만 참조한다.
  return { ...state, allRows: getAllTaxRows(state), rows: getSelectedTaxRows(state) };
};
