// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { create } from 'zustand';
import { sampleRows } from '../data/sampleRows';

export const EMPTY_SIM = { extraExp: 0, extraInc: 0, yellow: 0 };

// 세금 계산 화면에서 입력하는 수입/지출 내역, 프로필, 시뮬레이션 값
export const useTaxStore = create((set, get) => ({
  rows: sampleRows,
  pendingRows: [],
  profile: { type: '프리랜서(3.3% 원천징수)', auto33: true },
  sim: EMPTY_SIM,
  calculated: false,

  // 내역이 바뀌면 이전 계산 결과는 더 이상 맞지 않으므로 calculated를 되돌린다
  addRow: (row) => set((s) => ({ rows: [...s.rows, { id: `n${Date.now()}`, ...row }], calculated: false })),
  removeRow: (id) => set((s) => ({ rows: s.rows.filter((x) => x.id !== id), calculated: false })),
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

export const useTax = () => useTaxStore();
