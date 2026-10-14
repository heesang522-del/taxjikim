// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useState } from 'react';
import { Button, Card, Pill, Tabs } from '../components/common/index';
import { ROUTES } from '../utils/routes';
import { useChat } from '../store/useChatStore';
import { useToast } from '../store/useToastStore';
import { formatClockTime, formatDateTime, formatWaiting } from '../utils/chat';

const STATUSES = ['대기', '진행 중', '종료'];
const TONE = { 대기: 'warn', '진행 중': 'primary', 종료: 'neutral' };

/** 상담 관리: 관리자가 대기 목록에서 상담방을 직접 골라 입장하고 답변·종료한다. 새 메시지는 storage 이벤트로 실시간 반영된다. */
export default function AdminChatsPage() {
  const { rooms, enterRoom, sendAdmin, closeRoom, markAdminRead } = useChat();
  const toast = useToast();
  const [status, setStatus] = useState('대기');
  const [selectedId, setSelectedId] = useState(null);
  const [text, setText] = useState('');

  const list = rooms.filter((r) => r.status === status).sort((a, b) => (status === '대기' ? a.createdAt - b.createdAt : b.createdAt - a.createdAt));
  const room = rooms.find((r) => r.id === selectedId) ?? null;
  const unread = (r) => r.messages.filter((m) => m.from === 'member' && m.at > (r.adminReadAt ?? 0)).length;

  const open = (r) => {
    setSelectedId(r.id);
    setText('');
    if (r.status === '대기') { enterRoom(r.id); toast('상담방에 입장했습니다. 상태가 진행 중으로 바뀝니다'); setStatus('진행 중'); }
    else if (unread(r)) markAdminRead(r.id);
  };

  const send = (e) => {
    e.preventDefault();
    if (!text.trim()) { toast('답변 내용을 입력하세요'); return; }
    const delivery = sendAdmin(room.id, text);
    if (!delivery) { toast('이미 종료되었거나 입장하지 않은 상담방에는 보낼 수 없습니다'); return; }
    toast(delivery === 'online'
      ? '전송했습니다. 회원이 접속 중이라 채팅 패널로 바로 전달했습니다'
      : '전송했습니다. 회원이 접속 중이 아니라 메일을 발송하고 알림에 쌓아 두었습니다 (프로토타입: 실제 발송 없음)');
    setText('');
  };

  const end = () => { closeRoom(room.id); toast('상담을 종료했습니다. 종료 시점부터 30일간 보관합니다'); };

  return (
    <div className="grid">
      <div className="row between">
        <h2 className="post-title">상담 관리</h2>
        <Button to={`${ROUTES.admin}?tab=support`}>관리자 홈으로</Button>
      </div>
      <div className="chat-admin">
        <Card title="상담 목록" icon="messageSquare">
          <div style={{ marginBottom: 12 }}>
            <Tabs tabs={STATUSES.map((s) => ({ id: s, label: `${s} ${rooms.filter((r) => r.status === s).length}` }))} active={status} onChange={setStatus} />
          </div>
          {list.length ? list.map((r) => {
            const first = r.messages[0];
            return (
              <div key={r.id} style={{ padding: '10px 0', borderTop: '1px solid var(--border)' }}>
                <div className="row between">
                  <b>{r.nickname}</b>
                  <span className="row" style={{ gap: 6 }}>
                    {unread(r) > 0 && <Pill tone="danger">새 메시지 {unread(r)}</Pill>}
                    <Pill tone={TONE[r.status]}>{r.status}</Pill>
                  </span>
                </div>
                <p className="small" style={{ margin: '4px 0' }}>{first?.text.length > 40 ? `${first.text.slice(0, 40)}…` : first?.text}</p>
                <p className="small muted">{r.status === '대기' ? `대기 ${formatWaiting(r.createdAt)}` : r.status === '종료' ? `종료 ${formatDateTime(r.closedAt)}` : `시작 ${formatDateTime(r.startedAt)}`}</p>
                <div style={{ marginTop: 6 }}>
                  <Button size="sm" variant={r.status === '대기' ? 'primary' : 'soft'} onClick={() => open(r)}>{r.status === '대기' ? '입장' : '열기'}</Button>
                </div>
              </div>
            );
          }) : <p className="state-msg">{status} 상태의 상담이 없습니다</p>}
          <p className="small muted" style={{ marginTop: 8 }}>종료된 상담은 종료 후 30일이 지나면 삭제됩니다.</p>
        </Card>
        <Card title={room ? `${room.nickname} 님과의 상담` : '대화'} icon="messageSquare" aside={room && <Pill tone={TONE[room.status]}>{room.status}</Pill>}>
          {room ? (
            <>
              <div className="chat-body">
                {room.messages.map((m) => (
                  <div key={m.id} className={`chat-msg ${m.from === 'admin' ? 'member' : 'admin'}`} style={{ alignSelf: m.from === 'admin' ? 'flex-end' : 'flex-start' }}>
                    <p>{m.text}</p>
                    <span>{m.from === 'admin' ? '상담사' : room.nickname} · {formatClockTime(m.at)}{m.delivery && ` · ${m.delivery === 'online' ? '채팅 전달' : '메일 발송'}`}</span>
                  </div>
                ))}
              </div>
              {room.status === '종료'
                ? <p className="small muted" style={{ marginTop: 8 }}>종료된 상담에는 메시지를 보낼 수 없습니다.</p>
                : (
                  <form className="chat-form" style={{ padding: '10px 0 0', border: 0 }} onSubmit={send}>
                    <input className="in" aria-label="답변 입력" placeholder="답변을 입력하세요" maxLength={500} value={text} onChange={(e) => setText(e.target.value)} />
                    <Button type="submit" variant="primary" size="sm">보내기</Button>
                    <Button size="sm" variant="danger" onClick={end}>상담 종료</Button>
                  </form>
                )}
            </>
          ) : <p className="state-msg">목록에서 상담방을 골라 입장하세요</p>}
        </Card>
      </div>
    </div>
  );
}
