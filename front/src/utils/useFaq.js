// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useCallback, useEffect, useState } from 'react';
import { FAQ_SEED } from '../data/content';

const KEY = 'taxlab.faq.v1';
const isFaq = (f) => f && typeof f.id === 'string' && typeof f.q === 'string' && typeof f.a === 'string';

const read = () => {
  try {
    const list = JSON.parse(localStorage.getItem(KEY));
    if (Array.isArray(list)) return list.filter(isFaq);
  } catch { /* 저장값이 깨진 경우 예시 글로 시작 */ }
  return FAQ_SEED;
};

/** FAQ 목록. 관리자가 등록·수정·삭제하면 이 브라우저의 localStorage에 저장한다. */
export function useFaq() {
  const [faqs, setFaqs] = useState(read);

  useEffect(() => {
    try { localStorage.setItem(KEY, JSON.stringify(faqs)); } catch { /* 저장 공간이 없거나 막힌 경우 무시 */ }
  }, [faqs]);

  const addFaq = useCallback(({ q, a }) => {
    setFaqs((list) => [...list, { id: `f${Date.now()}`, q: q.trim(), a: a.trim() }]);
  }, []);
  const updateFaq = useCallback((id, { q, a }) => {
    setFaqs((list) => list.map((f) => (f.id === id ? { ...f, q: q.trim(), a: a.trim() } : f)));
  }, []);
  const removeFaq = useCallback((id) => setFaqs((list) => list.filter((f) => f.id !== id)), []);

  return { faqs, addFaq, updateFaq, removeFaq };
}
