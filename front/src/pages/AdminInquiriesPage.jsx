// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useEffect, useState } from 'react';
import { Button, Card, Pill } from '../components/common/index';
import { ROUTES } from '../utils/routes';
import { useInquiry } from '../stores/useInquiryStore';
import { useToast } from '../stores/useToastStore';
import { toDateLabel } from '../utils/format';

const PAGE_SIZE = 10; // 신고 접수 화면과 달리 문의는 수가 많아질 수 있어 칸마다 페이지로 끊어 보여준다.
const PAGE_WINDOW_SIZE = 9; // 페이지 번호 버튼은 한 번에 최대 이만큼만 보여준다 (게시판·고객센터와 동일)

// 전체 페이지가 많아져도 버튼이 끝없이 늘어나지 않도록, 현재 페이지를 중심으로 최대 size개만 잘라 보여준다.
function getPageWindow(current, total, size) {
  if (total <= size) return Array.from({ length: total }, (_, i) => i + 1);
  const start = Math.min(Math.max(1, current - Math.floor(size / 2)), total - size + 1);
  return Array.from({ length: size }, (_, i) => start + i);
}

function Pager({ page, totalPages, onChange }) {
  if (totalPages <= 1) return null;
  const numbers = getPageWindow(page, totalPages, PAGE_WINDOW_SIZE);
  return (
    <div className="row" style={{ justifyContent: 'center', gap: 6, marginTop: 16 }}>
      <Button size="sm" variant="ghost" disabled={page <= 1} onClick={() => onChange(page - 1)}>이전</Button>
      {numbers.map((n) => (
        <Button key={n} size="sm" variant={n === page ? 'primary' : 'ghost'} onClick={() => onChange(n)}>{n}</Button>
      ))}
      <Button size="sm" variant="ghost" disabled={page >= totalPages} onClick={() => onChange(page + 1)}>다음</Button>
    </div>
  );
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

function InquiryRow({ q, onSubmit, submitting }) {
  return (
    <details>
      <summary>
        {q.title}{' '}
        <Pill tone={q.visibility === 'public' ? 'primary' : 'neutral'}>{q.visibility === 'public' ? '공개' : '비공개'}</Pill>
      </summary>
      <p>{q.body}</p>
      {q.files?.length > 0 && <p className="small muted">첨부: {q.files.join(', ')}</p>}
      <p className="small muted">{q.author} · {toDateLabel(q.createdAt)} · <Button variant="link" size="sm" to={ROUTES.qnaPost(q.id)}>Q&A 상세 보기</Button></p>
      {q.status === '답변 완료'
        ? (q.answer
          ? <p style={{ marginTop: 6 }}><b>답변</b> {q.answer}</p>
          : <p className="small muted" style={{ marginTop: 6 }}>답변 내용은 상세 보기에서 확인할 수 있습니다.</p>)
        : <ReplyBox onSubmit={onSubmit} disabled={submitting} />}
    </details>
  );
}

export default function AdminInquiriesPage() {
  const { inquiries, loadInquiries, answerInquiry } = useInquiry();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [submittingId, setSubmittingId] = useState(null);
  const [pendingPage, setPendingPage] = useState(1);
  const [donePage, setDonePage] = useState(1);
  const toast = useToast();

  // 화면에 들어올 때마다 DB를 다시 읽어 신규 문의·답변 결과를 반영한다.
  useEffect(() => {
    let active = true;
    loadInquiries()
      .catch(() => { if (active) setError('문의 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [loadInquiries]);

  const pendingAll = inquiries.filter((q) => q.status !== '답변 완료').sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  const doneAll = inquiries.filter((q) => q.status === '답변 완료').sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  const pendingTotalPages = Math.max(1, Math.ceil(pendingAll.length / PAGE_SIZE));
  const doneTotalPages = Math.max(1, Math.ceil(doneAll.length / PAGE_SIZE));
  const pendingVisible = pendingAll.slice((pendingPage - 1) * PAGE_SIZE, pendingPage * PAGE_SIZE);
  const doneVisible = doneAll.slice((donePage - 1) * PAGE_SIZE, donePage * PAGE_SIZE);

  // 서버 문의는 댓글 등록 API로 답변하고(알림은 서버가 쌓는다), 브라우저 예시 문의는 기존처럼 로컬에만 저장한다.
  const submitAnswer = async (id, text) => {
    setSubmittingId(id);
    try {
      const delivery = await answerInquiry(id, text);
      toast(delivery === 'server'
        ? '답변을 등록했습니다. 작성자에게 알림이 전달됩니다'
        : delivery === 'online'
          ? '답변을 등록했습니다. 작성자가 접속 중이라 메일은 보내지 않고 알림으로 전달했습니다'
          : '답변을 등록했습니다. 작성자가 접속 중이 아니라 메일을 발송하고 알림에 쌓아 두었습니다 (프로토타입: 실제 발송 없음)');
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
          <>
            <Card title="대기중" icon="messageSquare" aside={<Pill tone="warn">{pendingAll.length}건</Pill>}>
              {pendingVisible.length
                ? pendingVisible.map((q) => (
                  <InquiryRow key={q.id} q={q} onSubmit={(text) => submitAnswer(q.id, text)} submitting={submittingId === q.id} />
                ))
                : <p className="state-msg">대기 중인 문의가 없습니다</p>}
              <Pager page={pendingPage} totalPages={pendingTotalPages} onChange={setPendingPage} />
            </Card>
            <Card title="처리됨" icon="messageSquare" aside={<Pill tone="success">{doneAll.length}건</Pill>}>
              {doneVisible.length
                ? doneVisible.map((q) => (
                  <InquiryRow key={q.id} q={q} onSubmit={(text) => submitAnswer(q.id, text)} submitting={submittingId === q.id} />
                ))
                : <p className="state-msg">처리된 문의가 없습니다</p>}
              <p className="small muted" style={{ marginTop: 8 }}>답변 등록 시 작성자에게 알림이 쌓입니다.</p>
              <Pager page={donePage} totalPages={doneTotalPages} onChange={setDonePage} />
            </Card>
          </>
        )}
    </div>
  );
}
