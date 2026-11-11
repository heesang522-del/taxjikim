import { useEffect, useState } from 'react';
import styled from 'styled-components';
import { Card, Icon } from '../common/index';
import { getMemberMessages, getMyChatHistory } from '../../api/chat';
import { useAuth } from '../../store/useAuthStore';
import { useMemberRoomStore } from '../../store/useMemberRoomStore';

function formatDate(value) {
  return value
    ? new Date(value).toLocaleString('ko-KR', {
        timeZone: 'Asia/Seoul',
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      })
    : '';
}

export function ConsultationHistory() {
  const { user } = useAuth();
  const [rooms, setRooms] = useState([]);
  const { room, roomError: openRoomError, loadRoom } = useMemberRoomStore();
  const openRoom = room?.status === 'CLOSED' ? null : room;
  const [selectedRoom, setSelectedRoom] = useState(null);
  const [messages, setMessages] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [historyError, setHistoryError] = useState('');
  const [messagesError, setMessagesError] = useState('');

  useEffect(() => {
    // 열린 상담은 채팅창과 같은 상태·요청을 공유하고 종료 상담 목록만 별도로 조회한다.
    setRooms([]);
    setSelectedRoom(null);
    setMessages([]);
    setHistoryError('');
    setMessagesError('');
    setLoading(true);
    loadRoom(user?.userId);
    getMyChatHistory()
      .then((result) => setRooms(result.data))
      .catch((error) => {
        setHistoryError(error.status && error.message ? error.message : '상담 내역을 불러오지 못했습니다.');
      })
      .finally(() => setLoading(false));
  }, [user?.userId, loadRoom]);

  function handleSelect(room) {
    if (loadingMessages) return;
    // 기록 열람은 GET만 사용하며 읽음 갱신·상담 연결·메시지 전송은 하지 않는다.
    setSelectedRoom(room);
    setMessages([]);
    setMessagesError('');
    setLoadingMessages(true);
    getMemberMessages(room.roomId)
      .then((result) => setMessages(result.data))
      .catch((error) => {
        setMessagesError(error.status && error.message ? error.message : '대화를 불러오지 못했습니다.');
      })
      .finally(() => setLoadingMessages(false));
  }

  return (
    <Card title="상담 내역" icon="messageSquare">
      <HistoryLayout>
        {openRoomError && (
          <p className="history-error" role="alert">
            {openRoomError}
          </p>
        )}
        {openRoom && (
          <div className="current-consultation" role="status">
            <Icon name="messageSquare" size={20} />
            <div>
              <strong>
                {openRoom.status === 'WAITING' ? '상담사 연결을 기다리고 있습니다.' : '진행 중인 상담이 있습니다.'}
              </strong>
              <p className="small">상담은 오른쪽 아래 채팅 버튼에서 이어갈 수 있어요.</p>
            </div>
          </div>
        )}
        <p className="small muted history-description">종료된 상담을 선택해 이전 대화를 확인하세요.</p>
        <div className="history-panel">
          <aside className="history-sidebar" aria-label="종료된 상담 목록">
            <div className="history-list-title">
              <strong>종료된 상담</strong>
              {!loading && !historyError && <span className="small muted">{rooms.length}건</span>}
            </div>
            <div className="history-list">
              {loading ? (
                <p className="state-msg">상담 내역을 불러오는 중입니다.</p>
              ) : historyError ? (
                <p className="history-error" role="alert">
                  {historyError}
                </p>
              ) : rooms.length === 0 ? (
                <p className="state-msg">종료된 상담이 없습니다.</p>
              ) : (
                rooms.map((room) => (
                  <button
                    type="button"
                    key={room.roomId}
                    className={`history-room ${selectedRoom?.roomId === room.roomId ? 'selected' : ''}`}
                    aria-pressed={selectedRoom?.roomId === room.roomId}
                    disabled={loadingMessages}
                    onClick={() => handleSelect(room)}
                  >
                    <span className="history-room-heading">
                      <strong>상담 #{room.roomId}</strong>
                      <span className="small">종료</span>
                    </span>
                    <span className="small muted">{formatDate(room.closedAt)}</span>
                    <span className="small history-preview">{room.lastMessage || '대화 내용 보기'}</span>
                  </button>
                ))
              )}
            </div>
          </aside>
          <section className="history-conversation" aria-label="상담 대화 내용">
            {selectedRoom ? (
              <>
                <header className="conversation-heading">
                  <div>
                    <h3>상담 #{selectedRoom.roomId}</h3>
                    <p className="small muted">{formatDate(selectedRoom.createdAt)} 시작</p>
                  </div>
                  <span className="readonly-label">읽기 전용</span>
                </header>
                <div className="history-messages" aria-busy={loadingMessages}>
                  {loadingMessages ? (
                    <p className="state-msg">대화를 불러오는 중입니다.</p>
                  ) : messagesError ? (
                    <p className="history-error" role="alert">
                      {messagesError}
                    </p>
                  ) : messages.length === 0 ? (
                    <p className="state-msg">저장된 메시지가 없습니다.</p>
                  ) : (
                    messages.map((message) => (
                      <div
                        className={`history-message ${message.senderId === user?.userId ? 'mine' : 'counselor'}`}
                        key={message.messageId}
                      >
                        <span className="small muted">{message.senderId === user?.userId ? '나' : '상담사'}</span>
                        <p>{message.content}</p>
                        <time className="small muted" dateTime={message.createdAt}>
                          {formatDate(message.createdAt)}
                        </time>
                      </div>
                    ))
                  )}
                </div>
                <p className="conversation-footer small muted">종료된 상담의 기록입니다. 메시지를 보낼 수 없습니다.</p>
              </>
            ) : (
              <div className="history-empty">
                <Icon name="messageSquare" size={24} />
                <h3>이전 상담을 확인해보세요</h3>
                <p className="small muted">왼쪽 목록에서 상담을 선택해주세요.</p>
              </div>
            )}
          </section>
        </div>
      </HistoryLayout>
    </Card>
  );
}

const HistoryLayout = styled.div`
  font-size: 13px;

  strong {
    font-weight: 600;
  }

  .history-error {
    padding: 12px;
    color: var(--danger);
    font-size: 14px;
  }
  .current-consultation {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 16px;
    margin-bottom: 20px;
    border-radius: 14px;
    background: var(--primary-bg);
    color: var(--primary);
  }
  .current-consultation p {
    margin-top: 4px;
  }
  .history-description {
    margin-bottom: 16px;
  }
  .history-panel {
    display: grid;
    grid-template-columns: 260px minmax(0, 1fr);
    border: 1px solid var(--border);
    border-radius: 18px;
    overflow: hidden;
  }
  .history-sidebar {
    min-width: 0;
    background: var(--fill);
    border-right: 1px solid var(--border);
  }
  .history-list-title {
    display: flex;
    justify-content: space-between;
    padding: 20px 16px;
  }
  .history-list {
    max-height: 300px;
    overflow-y: auto;
    padding: 0 10px 12px;
  }
  .history-room {
    display: grid;
    gap: 8px;
    width: 100%;
    padding: 14px 12px;
    margin-bottom: 8px;
    text-align: left;
    font: inherit;
    color: var(--text);
    background: var(--surface);
    border: 1px solid transparent;
    border-radius: 12px;
    cursor: pointer;
  }
  .history-room:hover,
  .history-room.selected {
    border-color: var(--primary-border);
    background: var(--primary-bg);
  }
  .history-room:disabled {
    cursor: wait;
  }
  .history-room-heading {
    display: flex;
    justify-content: space-between;
    gap: 8px;
  }
  .history-room-heading > span {
    color: var(--text-muted);
  }
  .history-preview {
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }
  .history-conversation {
    display: flex;
    flex-direction: column;
    min-width: 0;
    min-height: 280px;
  }
  .conversation-heading {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 20px;
    border-bottom: 1px solid var(--border);
  }
  .conversation-heading h3 {
    font-size: 14px;
    font-weight: 600;
    margin-bottom: 6px;
  }
  .readonly-label {
    padding: 5px 10px;
    border-radius: 8px;
    background: var(--fill);
    color: var(--text-muted);
    font-size: 12px;
    white-space: nowrap;
  }
  .history-messages {
    display: flex;
    flex-direction: column;
    gap: 20px;
    height: 260px;
    overflow-y: auto;
    padding: 24px 20px;
  }
  .history-message {
    display: grid;
    justify-items: start;
    gap: 6px;
    max-width: 85%;
    align-self: flex-start;
  }
  .history-message.mine {
    align-self: flex-end;
    justify-items: end;
  }
  .history-message p {
    padding: 12px 16px;
    border-radius: 14px;
    background: var(--fill);
    white-space: pre-wrap;
    overflow-wrap: anywhere;
  }
  .history-message.mine p {
    background: var(--primary-bg);
    color: var(--text);
  }
  .conversation-footer {
    padding: 14px 20px;
    border-top: 1px solid var(--border);
    text-align: center;
  }
  .history-empty {
    display: grid;
    align-content: center;
    justify-items: center;
    gap: 8px;
    flex: 1;
    padding: 24px 16px;
    text-align: center;
  }
  .history-empty > svg {
    color: var(--primary);
  }
  .history-empty h3 {
    font-size: 14px;
    font-weight: 500;
  }
  @media (max-width: 640px) {
    .history-panel {
      grid-template-columns: minmax(0, 1fr);
    }
    .history-sidebar {
      border-right: 0;
      border-bottom: 1px solid var(--border);
    }
    .history-list {
      max-height: 220px;
    }
  }
`;
