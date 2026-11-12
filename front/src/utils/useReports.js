// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useCallback, useEffect, useState } from 'react';
import { REPORT_SEED } from '../data/content';

const KEY = 'taxlab.reports.v2';
const isReport = (r) => r && [r.id, r.targetType, r.postId, r.reason, r.status].every((v) => typeof v === 'string');

const read = () => {
  try {
    const list = JSON.parse(localStorage.getItem(KEY));
    if (Array.isArray(list)) return list.filter(isReport);
  } catch { /* 저장값이 깨진 경우 예시 값으로 시작 */ }
  return REPORT_SEED;
};

/**
 * 신고 접수 목록과 처리 상태. 이 브라우저의 localStorage에 저장한다.
 * 신고는 targetType('post'|'comment')과 postId(, commentId)로 대상을 가리킨다.
 */
export function useReports() {
  const [reports, setReports] = useState(read);

  useEffect(() => {
    try { localStorage.setItem(KEY, JSON.stringify(reports)); } catch { /* 저장 공간이 없거나 막힌 경우 무시 */ }
  }, [reports]);

  useEffect(() => {
    const onStorage = (e) => { if (e.key === KEY) setReports(read()); };
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, []);

  const sameTarget = (r, t) => r.targetType === t.targetType && r.postId === t.postId && (r.commentId ?? null) === (t.commentId ?? null);

  /** 이미 내가 신고한 대상인지 */
  const hasReported = useCallback((reporter, target) => (
    reports.some((r) => r.reporter === reporter && sameTarget(r, target))
  ), [reports]);

  const addReport = useCallback(({ targetType, postId, commentId = null, reason, reporter }) => {
    const createdAt = new Date().toLocaleDateString('sv-SE');
    setReports((list) => [{ id: `r${Date.now()}`, targetType, postId, commentId, reason: reason.trim(), reporter, createdAt, status: '대기', result: null }, ...list]);
  }, []);

  /** result: '삭제' | '반려'. 삭제하면 같은 대상에 들어온 대기 중인 다른 신고도 함께 처리됨으로 바꾼다. */
  const resolveReport = useCallback((id, result) => {
    setReports((list) => {
      const target = list.find((r) => r.id === id);
      if (!target) return list;
      return list.map((r) => {
        if (r.id === id) return { ...r, status: '처리됨', result };
        if (result === '삭제' && r.status === '대기' && sameTarget(r, target)) return { ...r, status: '처리됨', result };
        return r;
      });
    });
  }, []);

  return { reports, addReport, resolveReport, hasReported };
}
