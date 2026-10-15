// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { create } from 'zustand';
import { ACCOUNT_SEED } from '../data/content.js';
import { loginSession, logoutSession } from '../api/authApi.js';
import { getMyProfile } from '../api/userApi.js';

const ACCOUNTS_KEY = 'taxlab.accounts.v1';
const LOCK_KEY = 'taxlab.login-lock.v1';
const PRESENCE_KEY = 'taxlab.presence.v1';

/** 로그인 실패를 허용하는 횟수. 이 횟수를 넘겨 실패하면(6번째 실패) 잠긴다. */
export const MAX_LOGIN_FAILS = 5;
/** 잠금 시간(초). 잠금이 풀린 뒤 또 실패할 때마다 다음 단계로 늘어난다. 마지막(5분) 잠금이 풀린 뒤에도 실패하면 계정이 이용 정지된다. */
export const LOCK_STEPS = [30, 60, 180, 300];
/** 접속 중으로 보는 시간(ms). 로그인한 탭이 이 주기로 접속 표시를 갱신한다. */
const PRESENCE_TTL = 45_000;
export const PRESENCE_BEAT = 15_000;

export const readJson = (key, fallback) => {
  try {
    const v = JSON.parse(localStorage.getItem(key));
    return v ?? fallback;
  } catch {
    return fallback;
  }
};
export const writeJson = (key, value) => {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    /* 저장 공간이 없거나 막힌 경우 무시 */
  }
};

// MOCK: 서버가 생기기 전까지 계정을 localStorage에 보관한다. 저장된 계정에 status가 없으면 정상으로 보고, 새로 추가된 예시 계정은 덧붙인다.
const readAccounts = () => {
  const saved = readJson(ACCOUNTS_KEY, null);
  if (!Array.isArray(saved)) return ACCOUNT_SEED;
  const list = saved.map((a) => ({ status: '정상', ...a }));
  return [...list, ...ACCOUNT_SEED.filter((seed) => !list.some((a) => a.id === seed.id))];
};

/** 접속 중인 닉네임인지 확인한다. 다른 탭에서 로그인한 계정도 localStorage 표시로 알 수 있다. */
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
/** 다른 탭에서 계정이 바뀌면(가입, 정지 등) 목록만 갱신한다. 이미 로그인한 세션은 다음 접속부터 정지가 적용된다. */
export const syncAccountsFromStorage = (key) => {
  if (key === ACCOUNTS_KEY) useAuthStore.setState({ accounts: readAccounts() });
};

function toScreenUser(profile) {
  return { ...profile, id: profile.loginId, role: profile.userRole === 'ADMIN' ? 'admin' : 'member' };
}

export const useAuthStore = create((set, get) => {
  let authRevision = 0;
  let initialRequest = null;
  // 계정 목록은 항상 저장된 최신 값에 변경을 적용해서 쓴다 (다른 탭의 변경을 덮어쓰지 않기 위해).
  const mutateAccounts = (fn) => {
    const next = fn(readAccounts());
    writeJson(ACCOUNTS_KEY, next);
    set({ accounts: next });
  };
  const findDuplicate = ({ id, nickname, email }) => {
    const { accounts } = get();
    if (accounts.some((a) => a.id === id)) return 'id';
    if (accounts.some((a) => a.nickname === nickname)) return 'nickname';
    if (accounts.some((a) => a.email.toLowerCase() === email.toLowerCase())) return 'email';
    return null;
  };

  return {
    accounts: readAccounts(),
    userId: null,
    authStatus: 'checking',
    authError: '',
    apiUser: null,
    sessionBlocked: false,
    clearSessionBlocked: () => set({ sessionBlocked: false }),

    /** 잠금 상태 확인. 잠겨 있으면 남은 초를 돌려준다. */
    lockLeft: (id) => {
      const rec = readJson(LOCK_KEY, {})[id];
      return rec && rec.until > Date.now() ? Math.ceil((rec.until - Date.now()) / 1000) : 0;
    },
    /** 가장 긴(마지막) 잠금 단계인지. 이 잠금이 풀린 뒤 또 실패하면 계정이 정지되므로 미리 알릴 때 쓴다. */
    lockIsFinal: (id) => {
      const rec = readJson(LOCK_KEY, {})[id];
      return Boolean(rec) && rec.level >= LOCK_STEPS.length;
    },

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

    login: async (id, pw) => {
      const revision = ++authRevision;
      try {
        // 응답 JWT는 저장하지 않고 쿠키로 인증된 회원 정보만 조회한다.
        await loginSession(id.trim(), pw);
        const result = await getMyProfile();
        if (revision !== authRevision)
          return { ok: false, message: '로그인 상태가 변경되었습니다. 다시 시도해주세요.' };
        const user = toScreenUser(result.data);
        set({ userId: user.loginId, apiUser: user, authStatus: 'ready', authError: '', sessionBlocked: false });
        return { ok: true };
      } catch (error) {
        return {
          ok: false,
          reason: error.code === 'BANNED' ? 'suspended' : 'error',
          message: error.message,
          code: error.code,
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

    /** 관리자가 계정을 정지·해제한다. 정상으로 되돌리면 로그인 실패 횟수와 잠금 단계도 초기화한다. */
    setAccountStatus: (id, status) => {
      mutateAccounts((list) =>
        list.map((a) => {
          if (a.id !== id) return a;
          const { suspendedBy, ...rest } = a;
          return status === '정지' ? { ...rest, status, suspendedBy: 'admin' } : { ...rest, status };
        })
      );
      if (status === '정상') {
        const locks = readJson(LOCK_KEY, {});
        delete locks[id];
        writeJson(LOCK_KEY, locks);
      }
    },

    /** 이미 쓰고 있는 값이면 어떤 항목인지 돌려준다 ('id' | 'nickname' | 'email'). */
    findDuplicate,

    signup: (data) => {
      const dup = findDuplicate(data);
      if (dup) return { ok: false, field: dup };
      const joinedAt = new Date().toLocaleDateString('sv-SE');
      mutateAccounts((list) => [...list, { ...data, role: 'member', status: '정상', joinedAt }]);
      return { ok: true };
    },

    findIdByNameEmail: (name, email) =>
      get().accounts.find((a) => a.name === name.trim() && a.email.toLowerCase() === email.trim().toLowerCase()) ??
      null,

    hasIdEmail: (id, email) =>
      get().accounts.some((a) => a.id === id.trim() && a.email.toLowerCase() === email.trim().toLowerCase()),

    resetPassword: (id, pw) => {
      mutateAccounts((list) => list.map((a) => (a.id === id.trim() ? { ...a, pw } : a)));
      const locks = readJson(LOCK_KEY, {});
      delete locks[id.trim()];
      writeJson(LOCK_KEY, locks);
    },
  };
});

/** 화면에서 쓰는 로그인 정보. 저장된 값(store)에서 user, loggedIn, isAdmin을 계산해 함께 돌려준다. */
export function useAuth() {
  const state = useAuthStore();
  const user = state.apiUser;
  return { ...state, user, loggedIn: Boolean(user), isAdmin: user?.role === 'admin' };
}
