import { create } from 'zustand'
import { dummyTransactions } from '../data/dummyTransactions'

export const useTransactionStore = create((set) => ({
  transactions: dummyTransactions,
  addTransaction: (transaction) =>
    set((state) => ({
      transactions: [
        ...state.transactions,
        { ...transaction, id: Date.now() },
      ],
    })),
  removeTransaction: (id) =>
    set((state) => ({
      transactions: state.transactions.filter((tx) => tx.id !== id),
    })),
}))
