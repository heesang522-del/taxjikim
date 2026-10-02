import { create } from 'zustand'
import { persist } from 'zustand/middleware'

// MOCK: 실제 인증 없이 로그인 여부만 저장한다 (새로고침해도 유지)
export const useAuthStore = create(
  persist(
    (set) => ({
      isLoggedIn: false,
      login: () => set({ isLoggedIn: true }),
      logout: () => set({ isLoggedIn: false }),
    }),
    { name: 'auth' },
  ),
)
