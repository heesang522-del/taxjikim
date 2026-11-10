// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useEffect, useRef, useState } from 'react';
import { Button, Icon } from '../common/index';
import { ROUTES } from '../../utils/routes';
import { useAuth } from '../../store/useAuthStore';
import { useChat, useMemberChat } from '../../store/useChatStore';
import { useToast } from '../../store/useToastStore';
import { BUSINESS_HOURS_TEXT, formatClockTime, isBusinessHours } from '../../utils/chat';

/** 모든 화면 우하단에 떠 있는 채팅 상담 버튼과 패널. 회원 전용이며 관리자는 상담 관리 화면을 쓴다. */
export default function ChatWidget() {
  const { loggedIn, isAdmin, user } = useAuth();
  const { open, setOpen } = useChat();
  // connected는 useMemberChat이 실제로 맺고 있는 WebSocket 소켓 상태를 그대로 반영한다.
  const { room, messages, loading, error, unreadCount, connected, send: sendMessage, close: closeChat } = useMemberChat(open, user?.userId, loggedIn);
  const toast = useToast();
  const [text, setText] = useState('');
  const endRef = useRef(null);
  // 아직 상담방이 없거나(첫 문의) 종료된 방이면 소켓을 연결하지 않으므로, 그 상태를 "끊김"으로 취급하지 않는다.
  const hasActiveRoom = Boolean(room) && room.status !== 'CLOSED';
  const disconnected = hasActiveRoom && !connected;

  const lastAt = messages[messages.length - 1]?.createdAt;
  // 상담사가 입장만 하고 아직 답장이 없을 때만 "기다리는 중" 문구를 보여준다.
  // room.status는 관리자가 입장해도 로컬에서 바로 갱신되지 않아 계속 WAITING으로 남을 수 있으므로,
  // 실제로 상담사가 보낸 메시지가 왔는지로 판단한다.
  const hasAdminReply = messages.some((m) => m.senderId !== user?.userId);

  // 메시지가 도착하거나 패널을 열면 맨 아래로 스크롤한다 (읽음 처리는 useMemberChat이 담당한다)
  useEffect(() => {
    if (!open) return;
    endRef.current?.scrollIntoView?.({ block: 'end' });
  }, [open, lastAt]);

  if (isAdmin) return null;

  const send = async (e) => {
    e.preventDefault();
    if (!text.trim()) { toast('메시지를 입력하세요'); return; }
    if (disconnected) { toast('연결이 끊어져 보낼 수 없습니다'); return; }
    const content = text;
    setText('');
    try {
      await sendMessage(content);
    } catch (cause) {
      toast(cause.message || '메시지를 보내지 못했습니다');
    }
  };

  const handleClose = async () => {
    try {
      await closeChat();
      // 종료 후에는 room.status가 CLOSED로 바뀌면서 채팅창에 "상담이 종료되었습니다" 안내가 뜬다. 토스트는 따로 띄우지 않는다.
    } catch (cause) {
      toast(cause.message || '상담을 종료하지 못했습니다');
    }
  };

  const hours = isBusinessHours();

  return (
    <div className="chat-root">
      {open && (
        <section className="chat-panel" role="dialog" aria-label="1:1 채팅 상담">
          <header className="chat-head">
            <b>1:1 채팅 상담</b>
            <button type="button" className="chat-x" aria-label="채팅 닫기" onClick={() => setOpen(false)}><Icon name="x" size={18} /></button>
          </header>
          {!loggedIn ? (
            <div className="chat-body chat-center">
              <p>채팅 상담은 로그인 후 이용할 수 있습니다.</p>
              <Button variant="primary" to={ROUTES.auth} onClick={() => setOpen(false)}>로그인</Button>
            </div>
          ) : (
            <>
              {disconnected && <p className="chat-warn" role="alert">연결이 끊어졌습니다. 다시 연결 중입니다…</p>}
              {error && <p className="chat-warn" role="alert">{error}</p>}
              <div className="chat-body">
                {loading && !room && <p className="chat-note">불러오는 중입니다…</p>}
                {!loading && room?.status === 'WAITING' && !hasAdminReply && <p className="chat-note">상담사를 기다리는 중입니다.</p>}
                {!loading && !room && <p className="chat-note">궁금한 점을 남겨 주세요.</p>}
                {messages.map((m) => {
                  const fromAdmin = m.senderId !== user?.userId;
                  return (
                    <div key={m.messageId} className={`chat-msg ${fromAdmin ? 'admin' : 'member'}`}>
                      <p>{m.content}</p>
                      <span>{fromAdmin ? '상담사 · ' : ''}{formatClockTime(m.createdAt)}</span>
                    </div>
                  );
                })}
                {/* 종료된 방은 마지막 메시지 아래에 안내만 띄우고 입력을 막는다. 새 상담은 패널을 닫았다가 다시 열어 시작한다 */}
                {room?.status === 'CLOSED' && (
                  <p className="chat-note">종료된 채팅방입니다. 새로운 채팅방을 만드시려면 패널을 다시 열고 메시지를 입력해주세요.</p>
                )}
                <div ref={endRef} />
              </div>
              <form className="chat-form" onSubmit={send}>
                <input
                  className="in"
                  aria-label="메시지 입력"
                  placeholder="메시지를 입력하세요"
                  maxLength={500}
                  value={text}
                  onChange={(e) => setText(e.target.value)}
                  disabled={room?.status === 'CLOSED'}
                />
                <Button type="submit" variant="primary" size="sm" disabled={disconnected || room?.status === 'CLOSED'}>보내기</Button>
                {hasActiveRoom && (
                  <Button variant="danger" size="sm" onClick={handleClose}>상담 종료</Button>
                )}
              </form>
              <p className="chat-foot">상담 시간 {BUSINESS_HOURS_TEXT}{!hours && ' · 지금은 상담 시간이 아닙니다. 남겨주신 메시지는 상담 시간에 확인 후 답변드립니다.'}</p>
            </>
          )}
        </section>
      )}
      <button type="button" className="chat-fab" aria-label="채팅 상담 열기" onClick={() => setOpen(!open)}>
        <Icon name="messageSquare" size={24} />
        {unreadCount > 0 && !open && <span className="badge" style={{ top: -2, right: -2 }}>{unreadCount}</span>}
      </button>
    </div>
  );
}
