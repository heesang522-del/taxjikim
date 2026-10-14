// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Button, Icon } from '../common/index';
import { ROUTES } from '../../utils/routes';
import { useAuth } from '../../store/useAuthStore';
import { useChat } from '../../store/useChatStore';
import { useToast } from '../../store/useToastStore';
import { BUSINESS_HOURS_TEXT, formatClockTime, isBusinessHours } from '../../utils/chat';

/** 브라우저 연결 상태. 끊기면 안내를 띄우고 다시 연결되면 자동으로 이어간다 (실서비스에서는 WebSocket 재연결). */
function useConnected() {
  const [ok, setOk] = useState(() => navigator.onLine);
  useEffect(() => {
    const on = () => setOk(true), off = () => setOk(false);
    window.addEventListener('online', on);
    window.addEventListener('offline', off);
    return () => { window.removeEventListener('online', on); window.removeEventListener('offline', off); };
  }, []);
  return ok;
}

/** 모든 화면 우하단에 떠 있는 채팅 상담 버튼과 패널. 회원 전용이며 관리자는 상담 관리 화면을 쓴다. */
export default function ChatWidget() {
  const { loggedIn, isAdmin } = useAuth();
  const { open, setOpen, myOpenRoom, myRooms, unreadCount, sendMember, markMemberRead } = useChat();
  const toast = useToast();
  const connected = useConnected();
  const [text, setText] = useState('');
  const endRef = useRef(null);

  // 가장 최근 상담(종료됐어도)의 대화를 보여준다. 종료된 방에 새로 보내면 새 상담방이 만들어진다.
  const room = myOpenRoom ?? myRooms[0] ?? null;
  const lastAt = room ? room.messages[room.messages.length - 1]?.at : 0;

  // 패널이 열려 있는 동안 도착한 메시지는 바로 읽음 처리하고 맨 아래로 스크롤한다
  useEffect(() => {
    if (!open || !room) return;
    if (room.messages.some((m) => m.from === 'admin' && m.at > (room.memberReadAt ?? 0))) markMemberRead(room.id);
    endRef.current?.scrollIntoView?.({ block: 'end' });
  }, [open, room, lastAt, markMemberRead]);

  if (isAdmin) return null;

  const send = (e) => {
    e.preventDefault();
    if (!text.trim()) { toast('메시지를 입력하세요'); return; }
    if (!connected) { toast('연결이 끊어져 보낼 수 없습니다'); return; }
    sendMember(text);
    setText('');
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
              {!connected && <p className="chat-warn" role="alert">연결이 끊어졌습니다. 다시 연결 중입니다…</p>}
              <div className="chat-body">
                <p className="chat-note">상담 시간 {BUSINESS_HOURS_TEXT}{!hours && ' · 지금은 상담 시간이 아닙니다. 남겨주신 메시지는 상담 시간에 확인 후 답변드립니다.'}</p>
                {room?.status === '대기' && <p className="chat-note">상담사를 기다리는 중입니다.</p>}
                {room?.status === '종료' && <p className="chat-note">상담이 종료되었습니다. 새 메시지를 보내면 새 상담이 시작됩니다.</p>}
                {!room && <p className="chat-note">궁금한 점을 남겨 주세요.</p>}
                {room?.messages.map((m) => (
                  <div key={m.id} className={`chat-msg ${m.from}`}>
                    <p>{m.text}</p>
                    <span>{m.from === 'admin' ? '상담사 · ' : ''}{formatClockTime(m.at)}</span>
                  </div>
                ))}
                <div ref={endRef} />
              </div>
              <form className="chat-form" onSubmit={send}>
                <input className="in" aria-label="메시지 입력" placeholder="메시지를 입력하세요" maxLength={500} value={text} onChange={(e) => setText(e.target.value)} />
                <Button type="submit" variant="primary" size="sm" disabled={!connected}>보내기</Button>
              </form>
              <p className="chat-foot">지난 상담은 <Link to={ROUTES.mypage} onClick={() => setOpen(false)}>마이페이지</Link>에서 볼 수 있어요.</p>
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
