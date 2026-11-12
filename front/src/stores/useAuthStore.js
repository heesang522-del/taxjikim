// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { create } from 'zustand';
import { loginSession, logoutSession, checkSession as checkSessionRequest } from '../api/authApi.js';
import { getMyProfile } from '../api/userApi.js';

const PRESENCE_KEY = 'taxlab.presence.v1';

/** 접속 중으로 보는 시간(ms). 로그인한 탭이 이 주기로 접속 표시를 갱신한다. */
const PRESENCE_TTL = 45_000;
export const PRESENCE_BEAT = 15_000;

// MOCK: 계정·잠금·접속 기록도 웹 스토리지에 남기지 않고 새로고침 시 초기화한다.
const mockMemory = new Map();
const readJson = (key, fallback) => mockMemory.get(key) ?? fallback;
const writeJson = (key, value) => mockMemory.set(key, value);

/** 현재 탭의 메모리에 기록된 예시 접속 상태만 확인한다. */
export const isOnline = (nickname) => {
  const at = readJson(PRESENCE_KEY, {})[nickname];
  return Number.isFinite(at) && Date.now() - at < PRESENCE_TTL;
};

// 인증 정보는 현재 페이지의 메모리에만 두고, 브라우저 저장소에서 복원하지 않는다.
/** 다른 mock 기능에서도 서버가 인증한 사용자 정보만 참조한다. */
export const getCurrentUser = () => useAuthStore.getState().apiUser;

/** 접속 표시 기록과 삭제 (StoreSync에서 사용) */
export const beatPresence = (nickname) =>
  writeJson(PRESENCE_KEY, { ...readJson(PRESENCE_KEY, {}), [nickname]: Date.now() });
export const clearPresence = (nickname) => {
  const map = readJson(PRESENCE_KEY, {});
  delete map[nickname];
  writeJson(PRESENCE_KEY, map);
};

function toScreenUser(profile) {
  return { ...profile, id: profile.loginId, role: profile.userRole === 'ADMIN' ? 'admin' : 'member' };
}

export const useAuthStore = create((set, get) => {
  let authRevision = 0;
  let initialRequest = null;
  return {
    userId: null,
    authStatus: 'checking',
    authError: '',
    apiUser: null,

    // 새로고침 시 브라우저가 보내는 HttpOnly 쿠키로 서버에 인증 여부를 확인한다.
    checkAuth: () => {
      if (initialRequest) return initialRequest;
      if (get().authStatus === 'ready') return Promise.resolve();
      const revision = authRevision;
      set({ authStatus: 'checking', authError: '' });
      initialRequest = getMyProfile()
        .then((result) => {
          if (revision !== authRevision) return;
          const user = toScreenUser(result.data);
          set({ userId: user.loginId, apiUser: user, authStatus: 'ready' });
        })
        .catch((error) => {
          if (revision !== authRevision) return;
          if (error.status === 401) set({ userId: null, apiUser: null, authStatus: 'ready' });
          else set({ authStatus: 'error', authError: error.message });
        })
        .finally(() => {
          initialRequest = null;
        });
      return initialRequest;
    },

    login: async (id, pw, rememberMe = false) => {
      const revision = ++authRevision;
      try {
        // 응답 JWT는 저장하지 않고 쿠키로 인증된 회원 정보만 조회한다.
        await loginSession(id.trim(), pw, rememberMe);
        const result = await getMyProfile();
        if (revision !== authRevision)
          return { ok: false, message: '로그인 상태가 변경되었습니다. 다시 시도해주세요.' };
        const user = toScreenUser(result.data);
        set({ userId: user.loginId, apiUser: user, authStatus: 'ready', authError: '' });
        return { ok: true };
      } catch (error) {
        return {
          ok: false,
          reason: error.code === 'BANNED' ? 'suspended' : 'error',
          message: error.message,
          code: error.code,
          details: error.details,
        };
      }
    },

    logout: async () => {
      ++authRevision;
      // 쿠키 삭제와 Redis 로그인 종료가 성공한 뒤 화면 상태를 비운다.
      await logoutSession();
      set({ userId: null, apiUser: null, authStatus: 'ready', authError: '' });
    },

    clearAuth: () => {
      ++authRevision;
      set({ userId: null, apiUser: null, authStatus: 'ready', authError: '' });
    },

    refreshProfile: async () => {
      const revision = authRevision;
      try {
        const result = await getMyProfile();
        // 조회 중 로그아웃·계정 변경이 발생하면 이전 응답은 화면에 반영하지 않는다.
        if (revision !== authRevision) return;
        const user = toScreenUser(result.data);
        set({ userId: user.loginId, apiUser: user });
      } catch (error) {
        if (revision === authRevision && error.status === 401) get().clearAuth();
        throw error;
      }
    },

    // 화면을 옮길 때마다 세션이 아직 유효한지만 확인한다. refreshProfile과 달리 프로필을 다시 받아오지
    // 않으므로 apiUser는 그대로 두고, 세션이 끝났을 때만(401) 로컬 상태를 비운다.
    checkSession: async () => {
      const revision = authRevision;
      try {
        await checkSessionRequest();
      } catch (error) {
        if (revision === authRevision && error.status === 401) get().clearAuth();
        throw error;
      }
    },
  };
});

/** 화면에서 쓰는 로그인 정보. 저장된 값(store)에서 user, loggedIn, isAdmin을 계산해 함께 돌려준다. */
export function useAuth() {
  const state = useAuthStore();
  const user = state.apiUser;
  return { ...state, user, loggedIn: Boolean(user), isAdmin: user?.role === 'admin' };
}
