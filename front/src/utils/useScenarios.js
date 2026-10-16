// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useCallback, useState } from 'react';
import { useAuth } from '../store/useAuthStore';
import { MAX_SCENARIOS } from '../data/options';

const KEY = 'taxlab.scenarios.v1';
const NAMES = ['A안', 'B안', 'C안'];
const isScenario = (s) => s && typeof s.name === 'string' && ['extraExp', 'extraInc', 'yellow'].every((k) => Number.isFinite(s[k]));

const read = (userId) => {
  if (!userId) return [];
  try {
    const list = JSON.parse(localStorage.getItem(`${KEY}.${userId}`));
    return Array.isArray(list) ? list.filter(isScenario) : [];
  } catch {
    return [];
  }
};

/**
 * 저장한 시나리오(회원 계정별, 최대 MAX_SCENARIOS개). 이 브라우저의 localStorage에 저장한다.
 * save(sim)은 'ok' | 'login'(비회원) | 'max'(가득 참) | 'error'(저장 실패)를 돌려준다.
 */
export function useScenarios() {
  const { user } = useAuth();
  const userId = user?.id ?? null;
  const [state, setState] = useState({ userId, list: read(userId) });
  // 로그인/로그아웃으로 사용자가 바뀌면 그 사용자의 저장값으로 바꾼다
  if (state.userId !== userId) setState({ userId, list: read(userId) });

  const persist = useCallback((list) => {
    try {
      localStorage.setItem(`${KEY}.${userId}`, JSON.stringify(list));
    } catch {
      return false;
    }
    setState({ userId, list });
    return true;
  }, [userId]);

  const save = useCallback((sim) => {
    if (!userId) return 'login';
    if (state.list.length >= MAX_SCENARIOS) return 'max';
    const name = NAMES.find((n) => !state.list.some((s) => s.name === n)) ?? `${state.list.length + 1}안`;
    return persist([...state.list, { extraExp: sim.extraExp, extraInc: sim.extraInc, yellow: sim.yellow, name }]) ? 'ok' : 'error';
  }, [userId, state.list, persist]);

  const remove = useCallback((name) => persist(state.list.filter((s) => s.name !== name)), [state.list, persist]);

  return { scenarios: state.list, save, remove };
}
