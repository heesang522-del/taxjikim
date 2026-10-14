// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useState } from 'react';
import { Button, Card, Pill } from '../components/common/index';
import { ROUTES } from '../utils/routes';
import { useInquiry } from '../store/useInquiryStore';
import { useToast } from '../store/useToastStore';

function ReplyBox({ onSubmit }) {
  const [text, setText] = useState('');
  const toast = useToast();
  const submit = () => {
    if (!text.trim()) { toast('답변 내용을 입력하세요'); return; }
    onSubmit(text);
    setText('');
  };
  return (
    <div className="grid" style={{ gap: 8, marginTop: 8 }}>
      <textarea className="in" rows={3} placeholder="답변 내용을 입력하세요" value={text} onChange={(e) => setText(e.target.value)} />
      <div><Button size="sm" variant="primary" onClick={submit}>답변 등록</Button></div>
    </div>
  );
}

export default function AdminInquiriesPage() {
  const { inquiries, answerInquiry } = useInquiry();
  const toast = useToast();
  const pending = inquiries.filter((q) => q.status !== '답변 완료').sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  const done = inquiries.filter((q) => q.status === '답변 완료').sort((a, b) => b.createdAt.localeCompare(a.createdAt));

  const submitAnswer = (id, text) => {
    const delivery = answerInquiry(id, text);
    toast(delivery === 'online'
      ? '답변을 등록했습니다. 작성자가 접속 중이라 메일은 보내지 않고 알림으로 전달했습니다'
      : '답변을 등록했습니다. 작성자가 접속 중이 아니라 메일을 발송하고 알림에 쌓아 두었습니다 (프로토타입: 실제 발송 없음)');
  };

  return (
    <div className="grid">
      <div className="row between">
        <h2 className="post-title">Q&A 문의 처리</h2>
        <Button to={`${ROUTES.admin}?tab=support`}>관리자 홈으로</Button>
      </div>
      <Card title="대기중" icon="messageSquare" aside={<Pill tone="warn">{pending.length}건</Pill>}>
        {pending.length
          ? pending.map((q) => (
            <details key={q.id}>
              <summary>{q.title} <Pill tone={q.visibility === 'public' ? 'primary' : 'neutral'}>{q.visibility === 'public' ? '공개' : '비공개'}</Pill></summary>
              <p>{q.body}</p>
              {q.files?.length > 0 && <p className="small muted">첨부: {q.files.join(', ')}</p>}
              <p className="small muted">{q.author} · {q.createdAt} · <Button variant="link" size="sm" to={ROUTES.qnaPost(q.id)}>Q&A 상세 보기</Button></p>
              <ReplyBox onSubmit={(text) => submitAnswer(q.id, text)} />
            </details>
          ))
          : <p className="state-msg">대기 중인 문의가 없습니다</p>}
      </Card>
      <Card title="처리됨" icon="messageSquare" aside={<Pill tone="success">{done.length}건</Pill>}>
        {done.length
          ? done.map((q) => (
            <details key={q.id}>
              <summary>{q.title} <Pill tone={q.visibility === 'public' ? 'primary' : 'neutral'}>{q.visibility === 'public' ? '공개' : '비공개'}</Pill></summary>
              <p>{q.body}</p>
              <p className="small muted">{q.author} · {q.createdAt} · <Button variant="link" size="sm" to={ROUTES.qnaPost(q.id)}>Q&A 상세 보기</Button></p>
              <p style={{ marginTop: 6 }}><b>답변</b> {q.answer}</p>
              {q.delivery && <p className="small muted">{q.delivery === 'online' ? '알림으로 전달 (작성자 접속 중이라 메일 미발송)' : '메일 발송 + 알림 적재 (작성자 미접속)'}</p>}
            </details>
          ))
          : <p className="state-msg">처리된 문의가 없습니다</p>}
        <p className="small muted" style={{ marginTop: 8 }}>답변 등록 시 작성자에게 알림이 쌓입니다. 작성자가 접속 중이 아니면 메일도 발송합니다.</p>
      </Card>
    </div>
  );
}
