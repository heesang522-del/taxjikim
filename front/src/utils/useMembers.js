// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useCallback, useMemo } from 'react';
import { useAuth } from '../store/useAuthStore';

/** 회원 목록과 이용 상태. 로그인 계정과 같은 데이터라서, 정지하면 그 계정은 로그인할 수 없다. */
export function useMembers() {
  const { accounts, setAccountStatus } = useAuth();

  const members = useMemo(() => accounts
    .filter((a) => a.role === 'member')
    .map((a) => ({ id: a.id, nickname: a.nickname, email: a.email, joinedAt: a.joinedAt, status: a.status })), [accounts]);

  /** 정상이면 정지로, 정지면 정상으로 바꾼다. 정지를 풀면 로그인 실패 횟수와 잠금 단계도 초기화된다. */
  const toggleStatus = useCallback((id) => {
    const m = members.find((x) => x.id === id);
    if (m) setAccountStatus(id, m.status === '정상' ? '정지' : '정상');
  }, [members, setAccountStatus]);

  return { members, toggleStatus };
}
