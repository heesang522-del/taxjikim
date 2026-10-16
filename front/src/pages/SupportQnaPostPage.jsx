// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useParams } from 'react-router-dom';
import { Button, Card, Pill } from '../components/common/index';
import NoticeCard from '../components/community/NoticeCard';
import { ROUTES } from '../utils/routes';
import { useAuth } from '../store/useAuthStore';
import { useInquiry } from '../store/useInquiryStore';

/** Q&A 상세. 공개 글은 누구나, 비공개 글은 작성자와 관리자만 볼 수 있다. */
export default function QnaPostPage() {
  const { id } = useParams();
  const { loggedIn, user, isAdmin } = useAuth();
  const { inquiries } = useInquiry();
  const q = inquiries.find((x) => x.id === id);
  const back = <Button variant="primary" to={ROUTES.helpQna}>Q&A 목록으로</Button>;

  if (!q) return <NoticeCard title="문의를 찾을 수 없습니다" desc="삭제되었거나 주소가 잘못되었습니다." action={back} />;

  const mine = loggedIn && q.author === user.nickname;
  if (q.visibility === 'private' && !mine && !isAdmin) {
    return (
      <NoticeCard
        title={loggedIn ? '볼 수 있는 권한이 없습니다' : '로그인이 필요합니다'}
        desc="비공개 문의는 작성자와 관리자만 볼 수 있습니다."
        action={loggedIn ? back : <Button variant="primary" to={ROUTES.auth}>로그인</Button>}
      />
    );
  }

  return (
    <Card>
      <div className="row" style={{ gap: 6 }}>
        <Pill tone={q.visibility === 'public' ? 'primary' : 'neutral'}>{q.visibility === 'public' ? '공개' : '비공개'}</Pill>
        <Pill tone={q.status === '답변 완료' ? 'success' : 'warn'}>{q.status}</Pill>
      </div>
      <h2 className="post-title">{q.title}</h2>
      <p className="small muted">{q.author} · {q.createdAt}</p>
      <div className="post-body">{q.body}</div>
      {q.files?.length > 0 && <p className="small muted" style={{ marginTop: 8 }}>첨부: {q.files.join(', ')}</p>}
      <div style={{ marginTop: 16, paddingTop: 12, borderTop: '1px solid var(--border)' }}>
        <b>관리자 답변</b>
        {q.answer
          ? <p className="post-body" style={{ marginTop: 6 }}>{q.answer}</p>
          : <p className="muted" style={{ marginTop: 6 }}>아직 답변이 등록되지 않았습니다.</p>}
      </div>
      <div className="row between" style={{ marginTop: 16 }}>
        <Button to={ROUTES.helpQna}>목록</Button>
        {isAdmin && q.status !== '답변 완료' && <Button variant="primary" to={ROUTES.adminInquiries}>답변하러 가기</Button>}
      </div>
    </Card>
  );
}
