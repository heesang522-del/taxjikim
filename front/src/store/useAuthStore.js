// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useMemo } from 'react';
import { create } from 'zustand';
import { ACCOUNT_SEED } from '../data/content';

const ACCOUNTS_KEY = 'taxlab.accounts.v1';
const LOCK_KEY = 'taxlab.login-lock.v1';
const PRESENCE_KEY = 'taxlab.presence.v1';
const SESSION_KEY = 'taxlab.session.v1';

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
  try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* 저장 공간이 없거나 막힌 경우 무시 */ }
};

// MOCK: 서버가 생기기 전까지 계정을 localStorage에 보관한다. 저장된 계정에 status가 없으면 정상으로 보고, 새로 추가된 예시 계정은 덧붙인다.
const readAccounts = () => {
  const saved = readJson(ACCOUNTS_KEY, null);
  if (!Array.isArray(saved)) return ACCOUNT_SEED;
  const list = saved.map((a) => ({ status: '정상', ...a }));
  return [...list, ...ACCOUNT_SEED.filter((seed) => !list.some((a) => a.id === seed.id))];
};
const toUser = (a) => ({ id: a.id, name: a.name, nickname: a.nickname, email: a.email, job: a.job, role: a.role });

/** 접속 중인 닉네임인지 확인한다. 다른 탭에서 로그인한 계정도 localStorage 표시로 알 수 있다. */
export const isOnline = (nickname) => {
  const at = readJson(PRESENCE_KEY, {})[nickname];
  return Number.isFinite(at) && Date.now() - at < PRESENCE_TTL;
};

// 새로고침·재접속 때 저장된 로그인이 이미 정지된 계정이면 로그인하지 않고 차단 안내로 보낸다 (sessionBlocked).
const readInitialSession = () => {
  let id = null;
  try { id = sessionStorage.getItem(SESSION_KEY); } catch { /* 세션 저장이 막힌 경우 무시 */ }
  const account = readAccounts().find((a) => a.id === id);
  return account && account.status === '정지' ? { userId: null, blocked: true } : { userId: account ? id : null, blocked: false };
};

/** 현재 로그인한 사용자(없으면 null). 다른 store의 동작에서 쓴다. */
export const getCurrentUser = () => {
  const { accounts, userId } = useAuthStore.getState();
  const a = accounts.find((x) => x.id === userId);
  return a ? toUser(a) : null;
};

/** 접속 표시 기록과 삭제 (StoreSync에서 사용) */
export const beatPresence = (nickname) => writeJson(PRESENCE_KEY, { ...readJson(PRESENCE_KEY, {}), [nickname]: Date.now() });
export const clearPresence = (nickname) => {
  const map = readJson(PRESENCE_KEY, {});
  delete map[nickname];
  writeJson(PRESENCE_KEY, map);
};
export const saveSession = (userId) => {
  try {
    if (userId) sessionStorage.setItem(SESSION_KEY, userId); else sessionStorage.removeItem(SESSION_KEY);
  } catch { /* 세션 저장이 막힌 경우 무시 */ }
};
/** 다른 탭에서 계정이 바뀌면(가입, 정지 등) 목록만 갱신한다. 이미 로그인한 세션은 다음 접속부터 정지가 적용된다. */
export const syncAccountsFromStorage = (key) => { if (key === ACCOUNTS_KEY) useAuthStore.setState({ accounts: readAccounts() }); };

export const useAuthStore = create((set, get) => {
  const init = readInitialSession();

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
    userId: init.userId,
    sessionBlocked: init.blocked,
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

    /**
     * MOCK: 서버가 하게 될 로그인 검증. 결과: { ok: true } 또는
     * { ok: false, reason: 'suspended' | 'locked' | 'no-id' | 'bad-pw', secondsLeft?, remaining?, finalLock? }
     * 정지된 계정은 비밀번호와 상관없이 'suspended'로 막는다. 반복 실패로 자동 정지될 때도 'suspended'다.
     */
    login: (id, pw) => {
      const { lockLeft, lockIsFinal } = get();
      const key = id.trim();
      const account = readAccounts().find((a) => a.id === key);
      if (account && account.status === '정지') return { ok: false, reason: 'suspended' };

      const left = lockLeft(key);
      if (left) return { ok: false, reason: 'locked', secondsLeft: left, finalLock: lockIsFinal(key) };

      if (account && account.pw === pw) {
        const locks = readJson(LOCK_KEY, {});
        delete locks[key];
        writeJson(LOCK_KEY, locks);
        set({ userId: account.id });
        return { ok: true };
      }

      const locks = readJson(LOCK_KEY, {});
      const prev = locks[key] || { fails: 0, level: 0, until: 0 };
      const fails = prev.fails + 1;
      let next = { ...prev, fails };
      let secondsLeft = 0;
      if (fails > MAX_LOGIN_FAILS) {
        // 마지막(5분) 잠금이 풀린 뒤에도 실패하면 잠그지 않고 계정을 이용 정지한다
        if (prev.level >= LOCK_STEPS.length) {
          writeJson(LOCK_KEY, { ...locks, [key]: { ...prev, fails } });
          if (account) mutateAccounts((list) => list.map((a) => (a.id === key ? { ...a, status: '정지', suspendedBy: 'auto' } : a)));
          return { ok: false, reason: 'suspended' };
        }
        secondsLeft = LOCK_STEPS[prev.level];
        next = { fails, level: prev.level + 1, until: Date.now() + secondsLeft * 1000 };
      }
      writeJson(LOCK_KEY, { ...locks, [key]: next });
      if (secondsLeft) return { ok: false, reason: 'locked', secondsLeft, finalLock: next.level >= LOCK_STEPS.length };
      return { ok: false, reason: account ? 'bad-pw' : 'no-id', remaining: MAX_LOGIN_FAILS - fails };
    },

    logout: () => set({ userId: null }),

    /** 관리자가 계정을 정지·해제한다. 정상으로 되돌리면 로그인 실패 횟수와 잠금 단계도 초기화한다. */
    setAccountStatus: (id, status) => {
      mutateAccounts((list) => list.map((a) => {
        if (a.id !== id) return a;
        const { suspendedBy, ...rest } = a;
        return status === '정지' ? { ...rest, status, suspendedBy: 'admin' } : { ...rest, status };
      }));
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

    findIdByNameEmail: (name, email) => (
      get().accounts.find((a) => a.name === name.trim() && a.email.toLowerCase() === email.trim().toLowerCase()) ?? null
    ),

    hasIdEmail: (id, email) => (
      get().accounts.some((a) => a.id === id.trim() && a.email.toLowerCase() === email.trim().toLowerCase())
    ),

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
  const user = useMemo(() => {
    const a = state.accounts.find((x) => x.id === state.userId);
    return a ? toUser(a) : null;
  }, [state.accounts, state.userId]);
  return { ...state, user, loggedIn: Boolean(user), isAdmin: user?.role === 'admin' };
}
