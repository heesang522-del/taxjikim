// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { create } from 'zustand';
import { INQUIRY_SEED } from '../data/content';
import { isOnline } from './useAuthStore';
import { getInquiries } from '../api/post.js';
import { createComment } from '../api/comment.js';

const KEY = 'taxlab.inquiry.v1';

const isInquiry = (q) => q
  && [q.id, q.title, q.body, q.author, q.visibility, q.status, q.createdAt].every((v) => typeof v === 'string')
  && (q.answer === null || typeof q.answer === 'string');

// 저장한 글이 있으면 그것을, 없거나 깨졌으면 예시 글을 쓴다.
const read = () => {
  try {
    const list = JSON.parse(localStorage.getItem(KEY));
    if (Array.isArray(list)) return list.filter(isInquiry);
  } catch { /* 저장값이 깨진 경우 예시 글로 시작 */ }
  return INQUIRY_SEED;
};

// 서버 문의와 브라우저 예시 문의를 합친다. 같은 id는 서버 데이터를 우선한다 (useBoardStore와 같은 방식).
const mergeInquiries = (serverInquiries, localInquiries) => {
  const serverIds = new Set(serverInquiries.map((q) => q.id));
  return [...serverInquiries, ...localInquiries.filter((q) => q.source !== 'server' && !serverIds.has(q.id))];
};

/** 다른 탭(예: 관리자 탭)에서 답변이 등록되면 바로 반영해 알림이 쌓이게 한다. */
export const syncInquiryFromStorage = (key) => {
  if (key !== KEY) return;
  const serverInquiries = useInquiryStore.getState().inquiries.filter((q) => q.source === 'server');
  useInquiryStore.setState({ inquiries: mergeInquiries(serverInquiries, read()) });
};

/** Q&A 글 목록. 서버의 실제 문의와 브라우저 예시 문의를 함께 보여준다. */
export const useInquiryStore = create((set, get) => {
  const setInquiries = (fn) => {
    const next = fn(get().inquiries);
    // 서버 문의는 로컬에 저장하지 않는다 (목록에 들어올 때마다 다시 불러온다).
    try { localStorage.setItem(KEY, JSON.stringify(next.filter((q) => q.source !== 'server'))); } catch { /* 저장 공간이 없거나 막힌 경우 무시 */ }
    set({ inquiries: next });
  };

  return {
    inquiries: read(),

    // 목록에 들어올 때마다 DB를 다시 읽어 신규 등록·답변 결과를 반영한다.
    loadInquiries: async () => {
      const serverInquiries = await getInquiries();
      set({ inquiries: mergeInquiries(serverInquiries.map((q) => ({ ...q, source: 'server' })), read()) });
    },

    /**
     * 관리자가 Q&A에 답하면 완료 처리한다. 서버 문의는 실제 댓글 등록 API로 답변을 보내고
     * (알림은 서버가 자동으로 쌓는다), 브라우저 예시 문의는 기존처럼 로컬에만 저장한다.
     */
    answerInquiry: async (id, answer) => {
      const trimmed = answer.trim();
      const target = get().inquiries.find((q) => q.id === id);

      if (target?.source === 'server') {
        await createComment(id, trimmed);
        setInquiries((list) => list.map((q) => (q.id === id ? { ...q, status: '답변 완료', answer: trimmed } : q)));
        return 'server';
      }

      const delivery = target && isOnline(target.author) ? 'online' : 'mail';
      setInquiries((list) => list.map((q) => (q.id === id ? { ...q, status: '답변 완료', answer: trimmed, delivery } : q)));
      return delivery;
    },
  };
});

export const useInquiry = () => useInquiryStore();
