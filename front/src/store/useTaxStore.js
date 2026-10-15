// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { create } from 'zustand';
import { sampleRows } from '../data/sampleRows';

export const EMPTY_SIM = { extraExp: 0, extraInc: 0, yellow: 0 };

export const getAllTaxRows = (state) => state.inputMethod === 'file' ? state.fileRows : state.manualRows;
export const getSelectedTaxRows = (state) => getAllTaxRows(state).filter((row) => row.selected !== false);

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
  addRow: (row) => set((s) => ({ manualRows: [...s.manualRows, { ...row, id: `n${crypto.randomUUID()}` }], calculated: false })),
  // 파일 내역과 직접 입력은 분리 보관하고 선택된 방식의 내역만 계산에 사용한다.
  setInputMethod: (method) => {
    if (!['manual', 'file'].includes(method) || method === get().inputMethod) return;
    set({ inputMethod: method, calculated: false, sim: EMPTY_SIM });
  },
  addFileResult: (rows, file) => set((state) => ({
    fileRows: [...state.fileRows, ...rows.map((row) => ({ ...row, sourceFileKey: file.key, selected: true, id: `f${crypto.randomUUID()}` }))],
    uploadedFiles: [...state.uploadedFiles, file],
    calculated: false,
  })),
  // 연말정산 증빙은 기재금액을 보관할 뿐, 장부 거래나 세금 계산 입력으로 바꾸지 않는다.
  addEvidenceResult: (evidence, file) => set((state) => ({
    evidenceDocuments: [...state.evidenceDocuments, { ...evidence, sourceFileKey: file.key, fileId: file.fileId, fileName: file.name }],
    uploadedFiles: state.uploadedFiles.some((entry) => entry.key === file.key) ? state.uploadedFiles : [...state.uploadedFiles, { ...file, documentKind: 'YEAR_END_EVIDENCE' }],
  })),
  // 회원 후보는 API 저장 성공 후 이 액션을 호출한다. 비회원은 현재 계산에만 반영한다.
  confirmReviewItem: (key, index, row) => set((state) => {
    const document = state.evidenceDocuments.find((entry) => entry.sourceFileKey === key);
    const item = document?.items[index];
    if (!item || item.confirmed) return {};
    return {
      fileRows: [...state.fileRows, { ...row, sourceFileKey: key, selected: true, id: `f${crypto.randomUUID()}` }],
      evidenceDocuments: state.evidenceDocuments.map((entry) => entry.sourceFileKey === key
        ? { ...entry, items: entry.items.map((value, i) => i === index ? { ...value, confirmed: true, persisted: row.persisted, transactionId: row.transactionId } : value) } : entry),
      calculated: false,
    };
  }),
  removeRow: (id) => set((state) => {
    const key = state.inputMethod === 'file' ? 'fileRows' : 'manualRows';
    return { [key]: state[key].filter((row) => row.id !== id), calculated: false };
  }),
  // 원본·DB 기록은 유지하고 현재 계산에 포함할 행만 선택하거나 목록에서 제거한다.
  setRowSelected: (id, selected) => set((state) => {
    const key = state.inputMethod === 'file' ? 'fileRows' : 'manualRows';
    return { [key]: state[key].map((row) => row.id === id ? { ...row, selected } : row), calculated: false };
  }),
  setAllRowsSelected: (selected) => set((state) => {
    const key = state.inputMethod === 'file' ? 'fileRows' : 'manualRows';
    return { [key]: state[key].map((row) => ({ ...row, selected })), calculated: false };
  }),
  setFileSelected: (key, selected) => set((state) => ({
    fileRows: state.fileRows.map((row) => row.sourceFileKey === key ? { ...row, selected } : row),
    calculated: false,
  })),
  removeUploadedFile: (key) => set((state) => ({
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
    get().addRow({ kind: merged.kind, date: merged.date, m: Math.min(12, Number(merged.date.slice(5, 7))), cat: merged.cat, memo: merged.memo, amt: merged.amt });
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
