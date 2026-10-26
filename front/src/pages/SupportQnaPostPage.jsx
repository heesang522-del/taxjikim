// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { Button, Card, Pill } from '../components/common/index';
import NoticeCard from '../components/community/NoticeCard';
import { ROUTES } from '../utils/routes';
import { useAuth } from '../store/useAuthStore';
import { useInquiry } from '../store/useInquiryStore';
import { getInquiryDetail } from '../api/post.js';
import { getComments } from '../api/comment.js';

// 서버 문의는 숫자 postId, 브라우저 예시 문의는 'q1' 같은 문자열 id를 쓴다.
const isServerId = (id) => /^\d+$/.test(id);

/** Q&A 상세. 공개 글은 누구나, 비공개 글은 작성자와 관리자만 볼 수 있다. */
export default function QnaPostPage() {
  const { id } = useParams();
  const { loggedIn, user, isAdmin } = useAuth();
  const { inquiries } = useInquiry();
  const [serverInquiry, setServerInquiry] = useState(null);
  const [loading, setLoading] = useState(isServerId(id));
  const [loadError, setLoadError] = useState(false);
  const back = <Button variant="primary" to={ROUTES.helpQna}>Q&A 목록으로</Button>;

  // 서버 문의는 상세 API로 직접 조회한다. 목록을 먼저 불러오지 않았어도 바로 들어올 수 있어야 한다.
  useEffect(() => {
    if (!isServerId(id)) return undefined;
    let active = true;
    setLoading(true);
    setLoadError(false);

    getInquiryDetail(id)
      .then(async (detail) => {
        // QNA 댓글은 전부 관리자 답변이다. 가장 최근 답변을 보여준다.
        const comments = await getComments(id).catch(() => []);
        const answer = comments.length ? comments[comments.length - 1].body : null;
        if (active) setServerInquiry({ ...detail, answer });
      })
      .catch(() => { if (active) setLoadError(true); })
      .finally(() => { if (active) setLoading(false); });

    return () => { active = false; };
  }, [id]);

  if (isServerId(id) && loading) {
    return <p className="state-msg" role="status">문의를 불러오는 중입니다.</p>;
  }

  const q = isServerId(id) ? serverInquiry : inquiries.find((x) => x.id === id);

  if (!q || loadError) {
    return <NoticeCard title="문의를 찾을 수 없습니다" desc="삭제되었거나 주소가 잘못되었습니다." action={back} />;
  }

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
