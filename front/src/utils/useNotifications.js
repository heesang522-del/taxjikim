// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useCallback, useMemo, useState } from 'react';
import { ROUTES } from './routes';
import { useAuth } from '../store/useAuthStore';
import { useBoard } from '../store/useBoardStore';
import { useInquiry } from '../store/useInquiryStore';

const KEY = 'taxlab.notif.seen.v1';

const readSeen = () => {
  try {
    const list = JSON.parse(localStorage.getItem(KEY));
    return Array.isArray(list) ? new Set(list) : new Set();
  } catch {
    return new Set();
  }
};

/** 새 공지 + 내 Q&A(공개·비공개) 답변을 알림으로 모은다. 확인하면 이 브라우저에 읽음으로 저장한다. */
export function useNotifications() {
  const { user } = useAuth();
  const { posts } = useBoard();
  const { inquiries } = useInquiry();
  const [seen, setSeen] = useState(readSeen);

  const items = useMemo(() => {
    const notices = posts
      .filter((p) => p.tab === '공지' && !p.deletedByAdmin)
      .map((p) => ({ id: `notice-${p.id}`, title: p.title, desc: '새 공지가 올라왔습니다', createdAt: p.createdAt, to: ROUTES.boardPost(p.id) }));
    const answers = inquiries
      .filter((q) => user && q.author === user.nickname && q.status === '답변 완료')
      .map((q) => ({
        id: `inquiry-${q.id}`,
        title: q.title,
        desc: 'Q&A에 답변이 등록되었습니다',
        createdAt: q.createdAt,
        to: ROUTES.qnaPost(q.id),
      }));
    // 채팅 상담 답변 알림은 실제 채팅 API 연동 전까지 별도 작업으로 남겨둔다.
    return [...notices, ...answers]
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .map((n) => ({ ...n, read: seen.has(n.id) }));
  }, [posts, inquiries, seen, user]);

  const unreadCount = items.filter((n) => !n.read).length;

  const markAllRead = useCallback(() => {
    setSeen((prev) => {
      const next = new Set(prev);
      items.forEach((n) => next.add(n.id));
      try { localStorage.setItem(KEY, JSON.stringify([...next])); } catch { /* 저장 공간이 없거나 막힌 경우 무시 */ }
      return next;
    });
  }, [items]);

  return { items, unreadCount, markAllRead };
}
