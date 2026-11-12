// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useCallback, useState } from 'react';

const KEY = 'taxlab.history.v1';
const isRecord = (r) => r && [r.year, r.income, r.expense, r.total].every(Number.isFinite);

const read = () => {
  try {
    const list = JSON.parse(localStorage.getItem(KEY));
    return Array.isArray(list) ? list.filter(isRecord) : [];
  } catch {
    return [];
  }
};

/** 연도별 계산 결과 기록. { year, income, expense, total }(원 단위). 이 브라우저의 localStorage에만 저장한다. */
export function useHistory() {
  const [records, setRecords] = useState(read);

  /** 같은 연도 기록은 덮어쓴다. 저장에 성공하면 true */
  const save = useCallback((record) => {
    const next = [...read().filter((r) => r.year !== record.year), record].sort((a, b) => a.year - b.year);
    try {
      localStorage.setItem(KEY, JSON.stringify(next));
    } catch {
      return false;
    }
    setRecords(next);
    return true;
  }, []);

  return { records, save };
}
