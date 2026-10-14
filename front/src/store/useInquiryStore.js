// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { create } from 'zustand';
import { INQUIRY_SEED } from '../data/content';
import { getCurrentUser, isOnline } from './useAuthStore';

const KEY = 'taxlab.inquiry.v1';

const isInquiry = (q) => q
  && [q.id, q.title, q.body, q.author, q.visibility, q.status, q.createdAt].every((v) => typeof v === 'string')
  && (q.answer === null || typeof q.answer === 'string');
const today = () => new Date().toLocaleDateString('sv-SE'); // YYYY-MM-DD

// 저장한 글이 있으면 그것을, 없거나 깨졌으면 예시 글을 쓴다.
const read = () => {
  try {
    const list = JSON.parse(localStorage.getItem(KEY));
    if (Array.isArray(list)) return list.filter(isInquiry);
  } catch { /* 저장값이 깨진 경우 예시 글로 시작 */ }
  return INQUIRY_SEED;
};

/** 다른 탭(예: 관리자 탭)에서 답변이 등록되면 바로 반영해 알림이 쌓이게 한다. */
export const syncInquiryFromStorage = (key) => { if (key === KEY) useInquiryStore.setState({ inquiries: read() }); };

/** Q&A 글 목록. 공개 글은 누구나, 비공개 글은 작성자와 관리자만 볼 수 있다 (목록 필터는 canView). MOCK: localStorage에 저장한다. */
export const useInquiryStore = create((set, get) => {
  const setInquiries = (fn) => {
    const next = fn(get().inquiries);
    try { localStorage.setItem(KEY, JSON.stringify(next)); } catch { /* 저장 공간이 없거나 막힌 경우 무시 */ }
    set({ inquiries: next });
  };

  return {
    inquiries: read(),

    addInquiry: ({ title, body, visibility, files = [] }) => {
      const inquiry = { id: `q${Date.now()}`, title: title.trim(), body: body.trim(), visibility, files, author: getCurrentUser()?.nickname ?? '', status: '답변 대기', answer: null, createdAt: today() };
      setInquiries((list) => [inquiry, ...list]);
      return inquiry.id;
    },

    /**
     * 관리자가 Q&A(공개·비공개 모두)에 답하면 완료 처리하고 작성자에게 알린다. 알림은 항상 쌓이고,
     * 작성자가 접속 중이면 메일은 보내지 않고(delivery 'online'), 접속 중이 아니면 메일을 보낸다(delivery 'mail').
     * MOCK: 실제 메일은 보내지 않고 발송 기록만 남긴다. 처리 방식('online' | 'mail')을 돌려준다.
     */
    answerInquiry: (id, answer) => {
      const target = get().inquiries.find((q) => q.id === id);
      const delivery = target && isOnline(target.author) ? 'online' : 'mail';
      setInquiries((list) => list.map((q) => (q.id === id ? { ...q, status: '답변 완료', answer: answer.trim(), delivery } : q)));
      return delivery;
    },
  };
});

export const useInquiry = () => useInquiryStore();
