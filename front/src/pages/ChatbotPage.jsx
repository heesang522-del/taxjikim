import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import {
  createConversation,
  deleteConversation,
  deleteLatestExchange,
  getChatbotUsage,
  getConversations,
  getMessages,
  sendMessage,
} from '../api/chatbotApi';
import '../styles/global.css';

export function ChatbotPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const parsedId = Number(searchParams.get('conversationId'));
  const conversationId = Number.isSafeInteger(parsedId) && parsedId > 0 ? parsedId : null;

  const [conversations, setConversations] = useState([]);
  const [messages, setMessages] = useState([]);
  const [usage, setUsage] = useState(null);
  const [question, setQuestion] = useState('');
  const [loading, setLoading] = useState(true);
  const [messagesLoading, setMessagesLoading] = useState(false);
  const [action, setAction] = useState('');
  const [errorMessage, setErrorMessage] = useState('');
  const [fieldError, setFieldError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);

  const endRef = useRef(null);
  const mountedRef = useRef(false);
  const busyRef = useRef(false);
  const composingRef = useRef(false);
  const selectedIdRef = useRef(conversationId);
  const messageRequestRef = useRef(0);

  const busy = action !== '';
  const selectedConversation = conversations.find((conversation) => conversation.conversationId === conversationId);

  // 마지막 두 메시지가 질문·답변일 때만 해당 질문에 삭제 버튼을 표시한다.
  const latestQuestion = messages[messages.length - 2];
  const latestAnswer = messages[messages.length - 1];
  const latestQuestionId =
    latestQuestion?.role?.toUpperCase() === 'USER' && latestAnswer?.role?.toUpperCase() === 'ASSISTANT'
      ? latestQuestion.messageId
      : null;

  useEffect(() => {
    mountedRef.current = true;

    return () => {
      mountedRef.current = false;
      messageRequestRef.current += 1;
    };
  }, []);

  // URL 이동으로 대화가 바뀐 경우에도 현재 선택 상태를 맞춘다.
  useEffect(() => {
    selectedIdRef.current = conversationId;
  }, [conversationId]);

  // 대화 목록과 사용량을 각각 처리해 조회에 성공한 결과는 유지한다.
  useEffect(() => {
    let active = true;

    async function loadOverview() {
      setLoading(true);
      setErrorMessage('');

      const results = await Promise.allSettled([getConversations(), getChatbotUsage()]);

      if (!active) return;

      const [conversationResult, usageResult] = results;

      if (conversationResult.status === 'fulfilled') {
        setConversations(conversationResult.value.data);
      }

      if (usageResult.status === 'fulfilled') {
        setUsage(usageResult.value.data);
      }

      const errors = results.filter((result) => result.status === 'rejected').map((result) => result.reason.message);

      setErrorMessage([...new Set(errors)].join(' '));
      setLoading(false);
    }

    loadOverview();

    return () => {
      active = false;
    };
  }, [reloadKey]);

  // 대화를 바꾸면 메시지를 다시 조회하고 이전 대화의 늦은 응답은 무시한다.
  useEffect(() => {
    let active = true;
    const requestId = ++messageRequestRef.current;

    setMessages([]);
    setQuestion('');
    setFieldError('');
    composingRef.current = false;

    if (!conversationId) {
      setMessagesLoading(false);
      return undefined;
    }

    setMessagesLoading(true);

    getMessages(conversationId)
      .then((result) => {
        if (active && requestId === messageRequestRef.current) {
          setMessages(result.data);
        }
      })
      .catch((cause) => {
        if (active && requestId === messageRequestRef.current) {
          setErrorMessage(cause.message);
        }
      })
      .finally(() => {
        if (active && requestId === messageRequestRef.current) {
          setMessagesLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [conversationId, reloadKey]);

  useEffect(() => {
    endRef.current?.scrollIntoView({ block: 'nearest' });
  }, [messages, action]);

  // 선택한 대화 ID를 주소에 남겨 새로고침 후에도 같은 대화를 표시한다.
  function selectConversation(nextId) {
    selectedIdRef.current = nextId;
    setErrorMessage('');

    setSearchParams((previous) => {
      const next = new URLSearchParams(previous);

      if (nextId === null) {
        next.delete('conversationId');
      } else {
        next.set('conversationId', String(nextId));
      }

      return next;
    });
  }

  function handleSelectConversation(nextId) {
    if (busyRef.current || nextId === conversationId) return;

    selectConversation(nextId);
  }

  // 빠른 연속 클릭과 Enter 반복 입력으로 요청이 중복되지 않게 한다.
  function beginAction(nextAction) {
    if (busyRef.current) return false;

    busyRef.current = true;
    setAction(nextAction);
    setErrorMessage('');
    setFieldError('');
    return true;
  }

  function finishAction() {
    busyRef.current = false;

    if (mountedRef.current) {
      setAction('');
    }
  }

  async function handleCreate() {
    if (!beginAction('creating')) return;

    try {
      const result = await createConversation();

      if (!mountedRef.current) return;

      setConversations((previous) => [
        result.data,
        ...previous.filter((item) => item.conversationId !== result.data.conversationId),
      ]);

      selectConversation(result.data.conversationId);
    } catch (cause) {
      if (mountedRef.current) {
        setErrorMessage(cause.message);
      }
    } finally {
      finishAction();
    }
  }

  async function handleDelete(targetId) {
    if (!beginAction('deleting')) return;

    try {
      await deleteConversation(targetId);

      if (!mountedRef.current) return;

      setConversations((previous) => previous.filter((item) => item.conversationId !== targetId));

      if (selectedIdRef.current === targetId) {
        selectConversation(null);
      }
    } catch (cause) {
      if (mountedRef.current) {
        setErrorMessage(cause.message);
      }
    } finally {
      finishAction();
    }
  }

  async function handleDeleteLatestExchange(messageId) {
    if (busyRef.current || !conversationId || messagesLoading || messageId !== latestQuestionId) {
      return;
    }

    const targetId = conversationId;

    if (!beginAction('deletingExchange')) return;

    // 기존 조회 응답이 삭제 후 메시지 목록을 덮어쓰지 않게 한다.
    const requestId = ++messageRequestRef.current;

    try {
      await deleteLatestExchange(targetId, messageId);

      if (!mountedRef.current) return;

      if (selectedIdRef.current === targetId) {
        // 삭제가 성공한 질문·답변은 재조회 실패 시에도 화면에서 제거한다.
        setMessages((previous) => {
          const questionIndex = previous.findIndex((message) => message.messageId === messageId);

          if (questionIndex < 0) return previous;

          const answer = previous[questionIndex + 1];

          return previous.filter(
            (message) => message.messageId !== messageId && message.messageId !== answer?.messageId
          );
        });

        setMessagesLoading(true);
      }
    } catch (cause) {
      if (!mountedRef.current) return;

      setErrorMessage(cause.message);

      if (selectedIdRef.current === targetId) {
        setMessagesLoading(true);
      }
    } finally {
      try {
        if (mountedRef.current) {
          // 다른 창의 변경이나 응답 유실도 반영하도록 서버 기록을 다시 조회한다.
          const results = await Promise.allSettled([getMessages(targetId), getConversations()]);

          if (mountedRef.current) {
            const [messageResult, conversationResult] = results;

            if (
              messageResult.status === 'fulfilled' &&
              selectedIdRef.current === targetId &&
              requestId === messageRequestRef.current
            ) {
              setMessages(messageResult.value.data);
            }

            if (conversationResult.status === 'fulfilled') {
              setConversations(conversationResult.value.data);
            }

            const errors = results
              .filter((result) => result.status === 'rejected')
              .map((result) => result.reason.message);

            if (errors.length > 0) {
              setErrorMessage((previous) => [...new Set([previous, ...errors].filter(Boolean))].join(' '));
            }
          }
        }
      } finally {
        if (mountedRef.current && selectedIdRef.current === targetId && requestId === messageRequestRef.current) {
          setMessagesLoading(false);
        }

        finishAction();
      }
    }
  }

  async function handleSubmit(event) {
    event.preventDefault();

    if (busyRef.current || !conversationId || messagesLoading || usage?.remaining === 0) {
      return;
    }

    const message = question.trim();
    const targetId = conversationId;

    // 백엔드와 동일하게 공백 제거 후 질문 길이를 검사한다.
    if (!message || message.length > 500) {
      setFieldError('질문은 1~500자로 입력해주세요.');
      return;
    }

    if (!beginAction('sending')) return;

    try {
      const result = await sendMessage(targetId, message);

      if (!mountedRef.current) return;

      if (selectedIdRef.current === targetId) {
        setQuestion('');
      }

      // 전송 응답에 포함된 남은 횟수를 우선 반영한다.
      setUsage((previous) => (previous ? { ...previous, remaining: result.data.remaining } : previous));
    } catch (cause) {
      if (!mountedRef.current) return;

      setErrorMessage(cause.message);

      if (selectedIdRef.current === targetId) {
        setFieldError(cause.fields?.message ?? '');
      }
    } finally {
      if (mountedRef.current) {
        const requestId = ++messageRequestRef.current;

        // 실패한 AI 호출도 사용량에 포함될 수 있어 서버 상태를 다시 조회한다.
        const results = await Promise.allSettled([getMessages(targetId), getChatbotUsage(), getConversations()]);

        if (mountedRef.current) {
          const [messageResult, usageResult, conversationResult] = results;

          if (
            messageResult.status === 'fulfilled' &&
            selectedIdRef.current === targetId &&
            requestId === messageRequestRef.current
          ) {
            // 서버에 저장된 마스킹된 질문과 답변을 표시한다.
            setMessages(messageResult.value.data);
          }

          if (usageResult.status === 'fulfilled') {
            setUsage(usageResult.value.data);
          }

          if (conversationResult.status === 'fulfilled') {
            setConversations(conversationResult.value.data);
          }

          const errors = results
            .filter((result) => result.status === 'rejected')
            .map((result) => result.reason.message);

          if (errors.length > 0) {
            setErrorMessage((previous) => [...new Set([previous, ...errors].filter(Boolean))].join(' '));
          }
        }
      }

      finishAction();
    }
  }

  function handleQuestionKeyDown(event) {
    // 한글 조합을 확정하는 Enter가 질문 전송으로 이어지지 않도록 한다.
    if (composingRef.current || event.nativeEvent.isComposing || event.keyCode === 229) {
      return;
    }

    // Shift+Enter는 textarea의 기본 줄바꿈 동작을 사용한다.
    if (event.key !== 'Enter' || event.shiftKey) return;

    event.preventDefault();

    // 키를 길게 누른 경우 반복 전송하지 않는다.
    if (event.repeat || busyRef.current) return;

    event.currentTarget.form?.requestSubmit();
  }

  function handleReload() {
    if (busyRef.current) return;

    setReloadKey((previous) => previous + 1);
  }

  function formatDate(value) {
    if (!value) return '';

    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '';

    return new Intl.DateTimeFormat('ko-KR', {
      timeZone: 'Asia/Seoul',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).format(date);
  }

  return (
    <section className="ai-chat-layout" aria-label="AI 챗봇">
      <aside className="ai-chat-sidebar">
        <div className="ai-chat-sidebar-header">
          <div className="row between">
            <h2>대화 목록</h2>
            <span className="small muted">{conversations.length}개</span>
          </div>

          <button type="button" disabled={busy || loading} onClick={handleCreate}>
            {action === 'creating' ? '생성 중...' : '+ 새 대화'}
          </button>
        </div>

        {loading ? (
          <p className="small muted" role="status">
            대화 목록을 불러오는 중입니다.
          </p>
        ) : conversations.length === 0 ? (
          <p className="small muted">저장된 대화가 없습니다.</p>
        ) : (
          <ul className="ai-chat-list">
            {conversations.map((conversation) => (
              <li key={conversation.conversationId} className="ai-chat-list-item">
                <button
                  type="button"
                  className="ai-chat-conversation"
                  aria-pressed={conversation.conversationId === conversationId}
                  disabled={busy}
                  onClick={() => handleSelectConversation(conversation.conversationId)}
                >
                  <span className="ai-chat-conversation-title">{conversation.title || '새 대화'}</span>
                  <span className="small muted">{formatDate(conversation.updatedAt ?? conversation.createdAt)}</span>
                </button>

                <button
                  type="button"
                  className="ai-chat-delete"
                  aria-label={`${conversation.title || '새 대화'} 삭제`}
                  disabled={busy}
                  onClick={() => handleDelete(conversation.conversationId)}
                >
                  삭제
                </button>
              </li>
            ))}
          </ul>
        )}

        <div className="ai-chat-sidebar-footer">
          {usage ? (
            <>
              <p className="small muted">{usage.usageDate} 기준</p>
              <p className="small">
                남은 질문 <strong>{usage.remaining}회</strong>
                {' / '}일일 한도 {usage.dailyLimit}회
              </p>
            </>
          ) : (
            <p className="small muted">사용량 정보를 확인해주세요.</p>
          )}
        </div>
      </aside>

      <div className="ai-chat-main">
        <header className="ai-chat-header">
          <div className="ai-chat-heading">
            <h1>AI 챗봇</h1>
            <p className="small muted">궁금한 내용을 질문하고 이전 대화를 확인하세요.</p>
            {selectedConversation && <p className="ai-chat-current-title">{selectedConversation.title || '새 대화'}</p>}
          </div>

          <button type="button" disabled={busy || loading || messagesLoading} onClick={handleReload}>
            다시 조회
          </button>
        </header>

        <div
          className="ai-chat-messages"
          aria-busy={messagesLoading || action === 'sending' || action === 'deletingExchange'}
        >
          {errorMessage && (
            <div className="ai-chat-error" role="alert">
              <p>{errorMessage}</p>
              <Link to="/login">로그인 화면으로 이동</Link>
            </div>
          )}

          {messagesLoading ? (
            <div className="ai-chat-empty" role="status">
              <p className="muted">대화 내용을 불러오는 중입니다.</p>
            </div>
          ) : !conversationId ? (
            <div className="ai-chat-empty">
              <h2>대화를 시작해보세요</h2>
              <p className="muted">
                새 대화를 만들거나
                <br />
                왼쪽 목록에서 이전 대화를 선택해주세요.
              </p>
            </div>
          ) : messages.length === 0 ? (
            <div className="ai-chat-empty">
              <h2>무엇이 궁금하신가요?</h2>
              <p className="muted">아래 입력창에 첫 질문을 작성해주세요.</p>
            </div>
          ) : (
            messages.map((message) => {
              const isUser = message.role?.toUpperCase() === 'USER';
              const canDelete = isUser && message.messageId === latestQuestionId;

              return (
                <article key={message.messageId} className={`ai-chat-message${isUser ? ' is-user' : ''}`}>
                  <strong>{isUser ? '나' : 'AI 챗봇'}</strong>
                  <p>{message.content}</p>

                  {canDelete && (
                    <button
                      type="button"
                      className="ai-chat-delete"
                      disabled={busy || loading || messagesLoading}
                      aria-label="가장 최근 질문과 AI 답변 함께 삭제"
                      onClick={() => handleDeleteLatestExchange(message.messageId)}
                    >
                      {action === 'deletingExchange' ? '삭제 중...' : '질문·답변 삭제'}
                    </button>
                  )}
                </article>
              );
            })
          )}

          {action === 'sending' && (
            <div className="ai-chat-waiting" role="status">
              <span className="ai-chat-waiting-dot" aria-hidden="true" />
              답변을 기다리는 중입니다.
            </div>
          )}

          {action === 'deletingExchange' && (
            <div className="ai-chat-waiting" role="status">
              질문과 답변을 삭제하고 대화를 다시 확인하는 중입니다.
            </div>
          )}

          <div ref={endRef} />
        </div>

        <div className="ai-chat-composer-area">
          <form className="ai-chat-composer" onSubmit={handleSubmit}>
            <div className="field">
              <div className="ai-chat-input-heading">
                <label htmlFor="ai-chat-question">질문</label>
                <span className="small muted">{question.length}/500자</span>
              </div>

              <textarea
                id="ai-chat-question"
                rows={3}
                maxLength={500}
                value={question}
                disabled={!conversationId || busy || messagesLoading || usage?.remaining === 0}
                aria-invalid={Boolean(fieldError)}
                aria-describedby={fieldError ? 'ai-chat-question-help ai-chat-question-error' : 'ai-chat-question-help'}
                placeholder={conversationId ? '질문을 입력해주세요.' : '먼저 대화를 생성하거나 선택해주세요.'}
                onCompositionStart={() => {
                  composingRef.current = true;
                }}
                onCompositionEnd={() => {
                  composingRef.current = false;
                }}
                onBlur={() => {
                  composingRef.current = false;
                }}
                onKeyDown={handleQuestionKeyDown}
                onChange={(event) => {
                  setQuestion(event.target.value);
                  setFieldError('');
                }}
              />
            </div>

            <button
              type="submit"
              disabled={!conversationId || busy || messagesLoading || !question.trim() || usage?.remaining === 0}
            >
              {action === 'sending' ? '답변 대기 중...' : '질문 보내기'}
            </button>
          </form>

          {fieldError && (
            <p id="ai-chat-question-error" className="ai-chat-error" role="alert">
              {fieldError}
            </p>
          )}

          <div className="ai-chat-composer-footer">
            <span id="ai-chat-question-help" className="small muted">
              Enter 전송 · Shift+Enter 줄바꿈 · 최대 500자
            </span>
            {usage && (
              <span className="small muted">
                {usage.remaining === 0 ? '오늘 질문 횟수를 모두 사용했습니다.' : `오늘 남은 질문 ${usage.remaining}회`}
              </span>
            )}
          </div>

          <p className="ai-chat-disclaimer small muted">
            AI 답변에는 오류가 있을 수 있으므로 중요한 내용은 근거를 확인해주세요.
          </p>
        </div>
      </div>
    </section>
  );
}
