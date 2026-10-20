// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useEffect, useRef, useState } from 'react';
import { Button, Card, Pill, Tabs } from '../components/common/index';
import { ROUTES } from '../utils/routes';
import { useAuth } from '../store/useAuthStore';
import { useAdminChat } from '../store/useChatStore';
import { useToast } from '../store/useToastStore';
import { formatClockTime, formatDateTime, formatWaiting } from '../utils/chat';

const STATUSES = ['WAITING', 'IN_PROGRESS', 'CLOSED'];
const LABEL = { WAITING: '대기', IN_PROGRESS: '진행 중', CLOSED: '종료' };
const TONE = { WAITING: 'warn', IN_PROGRESS: 'primary', CLOSED: 'neutral' };

/** 상담 관리: 관리자가 대기 목록에서 상담방을 직접 골라 입장하고 답변·종료한다. 실시간 송수신은 WebSocket으로 이루어진다. */
export default function AdminChatsPage() {
  const { user } = useAuth();
  const {
    status, setStatus, rooms, listLoading, listError, unreadByRoom,
    selectedRoom, messages, messagesLoading, connected, send, open, close, notice,
  } = useAdminChat(user?.userId);
  const toast = useToast();
  const [text, setText] = useState('');
  const bodyRef = useRef(null);

  const lastAt = messages[messages.length - 1]?.createdAt;

  // 메시지가 도착하거나 방을 열면 대화창만 맨 아래로 스크롤한다.
  // scrollIntoView는 대화창이 화면에 완전히 보이지 않을 때 페이지 전체를 스크롤시켜버리므로,
  // 대화창 자신의 scrollTop만 직접 옮겨서 페이지 스크롤에 영향을 주지 않게 한다.
  useEffect(() => {
    const el = bodyRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [selectedRoom?.roomId, lastAt]);

  const handleOpen = (room) => {
    setText('');
    open(room);
  };

  const handleSend = async (e) => {
    e.preventDefault();
    if (!text.trim()) { toast('답변 내용을 입력하세요'); return; }
    const content = text;
    setText('');
    try {
      await send(content);
    } catch (cause) {
      toast(cause.message || '전송하지 못했습니다');
    }
  };

  const handleClose = async () => {
    try {
      await close();
      toast('상담을 종료했습니다.');
    } catch (cause) {
      toast(cause.message || '종료하지 못했습니다');
    }
  };

  return (
    <div className="grid">
      <div className="row between">
        <h2 className="post-title">상담 관리</h2>
        <Button to={`${ROUTES.admin}?tab=support`}>관리자 홈으로</Button>
      </div>
      <div className="chat-admin">
        <Card title="상담 목록" icon="messageSquare">
          <div style={{ marginBottom: 12 }}>
            <Tabs tabs={STATUSES.map((s) => ({ id: s, label: LABEL[s] }))} active={status} onChange={setStatus} />
          </div>
          {notice && <p className="small" role="alert" style={{ color: 'var(--danger)', marginBottom: 8 }}>{notice}</p>}
          {listError && <p className="small" role="alert" style={{ color: 'var(--danger)' }}>{listError}</p>}
          {listLoading ? (
            <p className="state-msg">불러오는 중입니다.</p>
          ) : rooms.length ? (
            rooms.map((r) => (
              <div key={r.roomId} style={{ padding: '10px 0', borderTop: '1px solid var(--border)' }}>
                <div className="row between">
                  <b>{r.nickname}</b>
                  <span className="row" style={{ gap: 6 }}>
                    {unreadByRoom[r.roomId] > 0 && <Pill tone="danger">새 메시지 {unreadByRoom[r.roomId]}</Pill>}
                    <Pill tone={TONE[r.status]}>{LABEL[r.status]}</Pill>
                  </span>
                </div>
                <p className="small" style={{ margin: '4px 0' }}>
                  {r.lastMessage?.length > 40 ? `${r.lastMessage.slice(0, 40)}…` : r.lastMessage}
                </p>
                <p className="small muted">
                  {r.status === 'WAITING' ? `대기 ${formatWaiting(r.createdAt)}` : r.status === 'CLOSED' ? `종료 ${formatDateTime(r.closedAt)}` : `시작 ${formatDateTime(r.createdAt)}`}
                </p>
                <div style={{ marginTop: 6 }}>
                  <Button size="sm" variant={r.status === 'WAITING' ? 'primary' : 'soft'} onClick={() => handleOpen(r)}>
                    {r.status === 'WAITING' ? '입장' : '열기'}
                  </Button>
                </div>
              </div>
            ))
          ) : (
            <p className="state-msg">{LABEL[status]} 상태의 상담이 없습니다</p>
          )}
        </Card>
        <Card
          title={selectedRoom ? `${selectedRoom.nickname ?? ''} 님과의 상담` : '대화'}
          icon="messageSquare"
          aside={selectedRoom && <Pill tone={TONE[selectedRoom.status]}>{LABEL[selectedRoom.status]}</Pill>}
        >
          {selectedRoom ? (
            <>
              {selectedRoom.status === 'IN_PROGRESS' && !connected && (
                <p className="chat-warn" role="alert">연결 중입니다…</p>
              )}
              <div className="chat-body" ref={bodyRef}>
                {messagesLoading && <p className="state-msg">불러오는 중입니다.</p>}
                {messages.map((m) => {
                  const fromMember = m.senderId !== user?.userId;
                  return (
                    <div key={m.messageId} className="chat-msg" style={{ alignSelf: fromMember ? 'flex-start' : 'flex-end' }}>
                      <p>{m.content}</p>
                      <span>{fromMember ? (selectedRoom.nickname ?? '회원') : '상담사'} · {formatClockTime(m.createdAt)}</span>
                    </div>
                  );
                })}
              </div>
              {selectedRoom.status === 'CLOSED' ? (
                <p className="small muted" style={{ marginTop: 8 }}>종료된 상담에는 메시지를 보낼 수 없습니다.</p>
              ) : (
                <form className="chat-form" style={{ padding: '10px 0 0', border: 0 }} onSubmit={handleSend}>
                  <input className="in" aria-label="답변 입력" placeholder="답변을 입력하세요" maxLength={1000} value={text} onChange={(e) => setText(e.target.value)} />
                  <Button type="submit" variant="primary" size="sm">보내기</Button>
                  <Button size="sm" variant="danger" onClick={handleClose}>상담 종료</Button>
                </form>
              )}
            </>
          ) : (
            <p className="state-msg">목록에서 상담방을 골라 입장하세요</p>
          )}
        </Card>
      </div>
    </div>
  );
}
