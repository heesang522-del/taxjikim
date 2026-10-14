import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';
import { loginSession, logoutSession } from '../api/authApi.js';

// 실제 로그인 성공 응답만 저장하고, 같은 탭의 새로고침 동안 유지한다.
export const useAuthStore = create(
  persist(
    (set, get) => ({
      isLoggedIn: false,
      accessToken: null,
      user: null,
      login: async (loginId, password) => {
        const result = await loginSession(loginId, password);
        const { accessToken, userId, loginId: confirmedLoginId, userRole } = result.data;
        set({ isLoggedIn: true, accessToken, user: { userId, loginId: confirmedLoginId, userRole } });
      },
      logout: async () => {
        // 서버 세션 종료에 실패하면 로그인 상태를 유지하고 화면에서 오류를 알린다.
        await logoutSession(get().accessToken);
        set({ isLoggedIn: false, accessToken: null, user: null });
      },
    }),
    { name: 'auth-session', storage: createJSONStorage(() => sessionStorage) }
  )
);
