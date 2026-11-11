// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Pill } from '../components/common/index';
import { ROUTES } from '../utils/routes';
import { useInquiry } from '../store/useInquiryStore';
import { useToast } from '../store/useToastStore';
import { toDateLabel } from '../utils/format';
import { getInquiriesPage } from '../api/post.js';
import { createComment } from '../api/comment.js';

const PAGE_WINDOW_SIZE = 9; // 페이지 번호 버튼은 한 번에 최대 이만큼만 보여준다 (게시판·고객센터와 동일)

// 전체 페이지가 많아져도 버튼이 끝없이 늘어나지 않도록, 현재 페이지를 중심으로 최대 size개만 잘라 보여준다.
function getPageWindow(current, total, size) {
  if (total <= size) return Array.from({ length: total }, (_, i) => i + 1);
  const start = Math.min(Math.max(1, current - Math.floor(size / 2)), total - size + 1);
  return Array.from({ length: size }, (_, i) => start + i);
}

function ReplyBox({ onSubmit, disabled }) {
  const [text, setText] = useState('');
  const toast = useToast();
  const submit = () => {
    if (disabled) return;
    if (!text.trim()) { toast('답변 내용을 입력하세요'); return; }
    onSubmit(text);
    setText('');
  };
  return (
    <div className="grid" style={{ gap: 8, marginTop: 8 }}>
      <textarea className="in" rows={3} placeholder="답변 내용을 입력하세요" value={text} onChange={(e) => setText(e.target.value)} disabled={disabled} />
      <div><Button size="sm" variant="primary" onClick={submit} disabled={disabled}>{disabled ? '등록 중...' : '답변 등록'}</Button></div>
    </div>
  );
}

export default function AdminInquiriesPage() {
  // 서버 Q&A는 이 화면이 직접 페이지 단위로 불러온다. 브라우저에만 있는 예시 문의(있다면)는
  // 게시판·고객센터와 같은 방식으로 1페이지에만 함께 보여준다.
  const { inquiries: localInquiries, answerInquiry } = useInquiry();
  const [page, setPage] = useState(1);
  const [serverInquiries, setServerInquiries] = useState([]);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [submittingId, setSubmittingId] = useState(null);
  const toast = useToast();

  const fetchPage = useCallback(async (targetPage) => {
    const { posts: items, meta } = await getInquiriesPage({ page: targetPage });
    setTotalPages(meta.totalPages || 1);
    setServerInquiries(items.map((q) => ({ ...q, source: 'server' })));
  }, []);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    fetchPage(page)
      .catch(() => { if (active) setError('문의 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [page, fetchPage]);

  const localOnly = localInquiries.filter((q) => q.source !== 'server');
  const visible = (page === 1 ? [...localOnly, ...serverInquiries] : serverInquiries)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  const pageNumbers = getPageWindow(page, totalPages, PAGE_WINDOW_SIZE);

  // 서버 문의는 실제 댓글 등록 API로 답변하고(알림은 서버가 쌓는다) 현재 페이지를 다시 받아온다.
  // 브라우저 예시 문의는 기존처럼 store에서 로컬로 처리한다.
  const submitAnswer = async (q, text) => {
    setSubmittingId(q.id);
    try {
      if (q.source === 'server') {
        await createComment(q.id, text.trim());
        await fetchPage(page);
        toast('답변을 등록했습니다. 작성자에게 알림이 전달됩니다');
      } else {
        const delivery = await answerInquiry(q.id, text);
        toast(delivery === 'online'
          ? '답변을 등록했습니다. 작성자가 접속 중이라 메일은 보내지 않고 알림으로 전달했습니다'
          : '답변을 등록했습니다. 작성자가 접속 중이 아니라 메일을 발송하고 알림에 쌓아 두었습니다 (프로토타입: 실제 발송 없음)');
      }
    } catch (err) {
      toast(err.message || '답변 등록 중 오류가 발생했습니다.');
    } finally {
      setSubmittingId(null);
    }
  };

  return (
    <div className="grid">
      <div className="row between">
        <h2 className="post-title">Q&A 문의 처리</h2>
        <Button to={`${ROUTES.admin}?tab=support`}>관리자 홈으로</Button>
      </div>
      {loading ? <p className="state-msg" role="status">문의 목록을 불러오는 중입니다.</p>
        : error ? <p className="state-msg" role="alert">{error}</p>
        : (
          <Card title="문의 목록" icon="messageSquare" aside={<Pill tone="warn">{visible.filter((q) => q.status !== '답변 완료').length}건 대기</Pill>}>
            {visible.length
              ? visible.map((q) => (
                <details key={q.id}>
                  <summary>
                    {q.title}{' '}
                    <Pill tone={q.visibility === 'public' ? 'primary' : 'neutral'}>{q.visibility === 'public' ? '공개' : '비공개'}</Pill>{' '}
                    <Pill tone={q.status === '답변 완료' ? 'success' : 'warn'}>{q.status}</Pill>
                  </summary>
                  <p>{q.body}</p>
                  {q.files?.length > 0 && <p className="small muted">첨부: {q.files.join(', ')}</p>}
                  <p className="small muted">{q.author} · {toDateLabel(q.createdAt)} · <Button variant="link" size="sm" to={ROUTES.qnaPost(q.id)}>Q&A 상세 보기</Button></p>
                  {q.status === '답변 완료'
                    ? (q.answer
                      ? <p style={{ marginTop: 6 }}><b>답변</b> {q.answer}</p>
                      : <p className="small muted" style={{ marginTop: 6 }}>답변 내용은 상세 보기에서 확인할 수 있습니다.</p>)
                    : <ReplyBox onSubmit={(text) => submitAnswer(q, text)} disabled={submittingId === q.id} />}
                </details>
              ))
              : <p className="state-msg">등록된 문의가 없습니다</p>}
            {totalPages > 1 && (
              <div className="row" style={{ justifyContent: 'center', gap: 6, marginTop: 16 }}>
                <Button size="sm" variant="ghost" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>이전</Button>
                {pageNumbers.map((n) => (
                  <Button key={n} size="sm" variant={n === page ? 'primary' : 'ghost'} onClick={() => setPage(n)}>{n}</Button>
                ))}
                <Button size="sm" variant="ghost" disabled={page >= totalPages} onClick={() => setPage((p) => p + 1)}>다음</Button>
              </div>
            )}
          </Card>
        )}
    </div>
  );
}
