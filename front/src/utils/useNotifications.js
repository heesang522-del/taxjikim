// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useCallback, useMemo, useState } from 'react';
import { ROUTES } from './routes';
import { useAuth } from '../store/useAuthStore';
import { useBoard } from '../store/useBoardStore';
import { useInquiry } from '../store/useInquiryStore';
import { useChat } from '../store/useChatStore';

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
  const { myRooms } = useChat();
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
    // 회원이 접속하지 않은 동안 도착한(메일로 처리된) 상담 답변은 알림에 쌓는다. 접속 중에 받은 답변은 채팅 패널·배지로만 전달한다.
    const chats = myRooms.flatMap((r) => r.messages
      .filter((m) => m.from === 'admin' && m.delivery === 'mail')
      .map((m) => ({
        id: `chat-${m.id}`,
        title: '상담 답변이 도착했습니다',
        desc: m.text.length > 30 ? `${m.text.slice(0, 30)}…` : m.text,
        createdAt: new Date(m.at).toLocaleDateString('sv-SE'),
        at: m.at,
        action: 'chat',
      })));
    return [...notices, ...answers, ...chats]
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .map((n) => ({ ...n, read: seen.has(n.id) }));
  }, [posts, inquiries, myRooms, seen, user]);

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
