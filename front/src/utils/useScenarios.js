// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useCallback, useEffect, useState } from 'react';
import { useAuth } from '../store/useAuthStore';
import { MAX_SCENARIOS } from '../data/options';

const KEY = 'taxlab.scenarios.v1';
const CHANGED_EVENT = 'taxlab:scenarios-changed';
const NAMES = ['A안', 'B안', 'C안'];
// 기존 노란우산 형식도 보관하되 새 시나리오는 기부금·서버 계산 결과를 저장한다.
const isScenario = (s) =>
  s &&
  typeof s.name === 'string' &&
  ['extraExp', 'extraInc'].every((k) => Number.isFinite(s[k])) &&
  (Number.isFinite(s.extraDonation) || Number.isFinite(s.yellow));

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

  // 같은 화면의 저장 버튼·목록은 각각 이 Hook을 사용하므로 변경 사실을 함께 알린다.
  useEffect(() => {
    function refresh() {
      setState({ userId, list: read(userId) });
    }
    window.addEventListener(CHANGED_EVENT, refresh);
    window.addEventListener('storage', refresh);
    return () => {
      window.removeEventListener(CHANGED_EVENT, refresh);
      window.removeEventListener('storage', refresh);
    };
  }, [userId]);

  const persist = useCallback(
    (list) => {
      try {
        localStorage.setItem(`${KEY}.${userId}`, JSON.stringify(list));
      } catch {
        return false;
      }
      setState({ userId, list });
      window.dispatchEvent(new Event(CHANGED_EVENT));
      return true;
    },
    [userId]
  );

  const save = useCallback(
    (sim) => {
      if (!userId) return 'login';
      const list = read(userId);
      if (list.length >= MAX_SCENARIOS) return 'max';
      const name = NAMES.find((n) => !list.some((s) => s.name === n)) ?? `${list.length + 1}안`;
      return persist([
        ...list,
        {
          extraInc: sim.extraInc,
          extraExp: sim.extraExp,
          extraDonation: sim.extraDonation ?? 0,
          totalTax: sim.totalTax,
          name,
        },
      ])
        ? 'ok'
        : 'error';
    },
    [userId, persist]
  );

  const remove = useCallback((name) => persist(read(userId).filter((s) => s.name !== name)), [userId, persist]);

  return { scenarios: state.list, save, remove };
}
