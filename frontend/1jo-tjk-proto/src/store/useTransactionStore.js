import { create } from 'zustand'
import { initialTransactions, sampleTransactions } from '../data/dummyTransactions'

// 세금 계산 화면에서 입력하는 거래 내역 (수입/지출)
export const useTransactionStore = create((set) => ({
  transactions: initialTransactions,
  addTransaction: (transaction) =>
    set((state) => ({
      transactions: [...state.transactions, { ...transaction, id: Date.now() }],
    })),
  // 표의 한 칸이 바뀔 때 해당 행의 바뀐 필드만 덮어쓴다
  updateTransaction: (id, patch) =>
    set((state) => ({
      transactions: state.transactions.map((tx) => (tx.id === id ? { ...tx, ...patch } : tx)),
    })),
  removeTransaction: (id) =>
    set((state) => ({
      transactions: state.transactions.filter((tx) => tx.id !== id),
    })),
  loadSample: () => set({ transactions: sampleTransactions }),
}))
