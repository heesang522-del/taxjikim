// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Button, Card, Modal, Pill } from '../components/common/index';
import NoticeCard from '../components/community/NoticeCard';
import CommentSection from '../components/community/CommentSection';
import ReportModal from '../components/community/ReportModal';
import { DELETED_POST } from '../data/options';
import { ROUTES } from '../utils/routes';
import { useAuth } from '../store/useAuthStore';
import { useBoard } from '../store/useBoardStore';
import { useToast } from '../store/useToastStore';
import { useReports } from '../utils/useReports';

const listOf = (tab) => `${ROUTES.board}?tab=${encodeURIComponent(tab)}`;

export default function BoardPostPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  const { loggedIn, user } = useAuth();
  const { addReport, hasReported } = useReports();
  const { posts, addView, removePost } = useBoard();
  const [confirm, setConfirm] = useState(false);
  const [reporting, setReporting] = useState(false);
  const post = posts.find((p) => p.id === id);
  const counted = useRef(null);

  // 글을 열 때 조회수를 한 번만 올린다 (개발 모드의 이펙트 이중 실행 방지)
  const exists = Boolean(post);
  useEffect(() => {
    if (exists && counted.current !== id) { counted.current = id; addView(id); }
  }, [exists, id, addView]);

  if (!post) {
    return <NoticeCard title="게시글을 찾을 수 없습니다" desc="삭제되었거나 주소가 잘못되었습니다." action={<Button variant="primary" to={ROUTES.board}>목록으로</Button>} />;
  }

  if (post.deletedByAdmin) {
    return <NoticeCard title={DELETED_POST} desc="운영 정책에 따라 삭제된 글입니다." action={<Button variant="primary" to={listOf(post.tab)}>목록으로</Button>} />;
  }

  const mine = loggedIn && post.author === user.nickname;
  const report = (reason) => {
    const target = { targetType: 'post', postId: post.id };
    if (hasReported(user.nickname, target)) toast('이미 신고한 글입니다');
    else { addReport({ ...target, reason, reporter: user.nickname }); toast('신고를 접수했습니다'); }
    setReporting(false);
  };
  const remove = () => {
    removePost(post.id);
    toast('글을 삭제했습니다');
    navigate(listOf(post.tab), { replace: true });
  };

  return (
    <>
      <Card>
        <Pill tone={post.tab === '공지' ? 'danger' : 'primary'}>{post.tab}</Pill>
        <h2 className="post-title">{post.title}</h2>
        <p className="small muted">{post.author} · {post.createdAt} · 조회 {post.views.toLocaleString('ko-KR')}</p>
        <div className="post-body">{post.body}</div>
        {post.files?.length > 0 && <p className="small muted" style={{ marginTop: 8 }}>첨부: {post.files.join(', ')}</p>}
        <div className="row between" style={{ marginTop: 16 }}>
          <Button to={listOf(post.tab)}>목록</Button>
          {mine && (
            <div className="row">
              <Button variant="soft" to={ROUTES.boardEdit(post.id)}>수정</Button>
              <Button variant="danger" onClick={() => setConfirm(true)}>삭제</Button>
            </div>
          )}
          {!mine && <Button variant="ghost" onClick={() => { if (loggedIn) setReporting(true); else { toast('신고는 로그인 후 이용할 수 있습니다'); navigate(ROUTES.auth); } }}>신고</Button>}
        </div>
      </Card>
      <CommentSection postId={post.id} />
      {reporting && <ReportModal targetLabel="게시글" onSubmit={report} onClose={() => setReporting(false)} />}
      {confirm && (
        <Modal title="글 삭제" onClose={() => setConfirm(false)}>
          <div className="grid" style={{ gap: 16, textAlign: 'center' }}>
            <div>
              <h3 style={{ fontSize: 18, marginBottom: 6 }}>이 글을 삭제할까요?</h3>
              <p className="small muted">삭제한 글은 되돌릴 수 없습니다.</p>
            </div>
            <div className="grid" style={{ gap: 8 }}>
              <Button variant="danger" size="lg" onClick={remove}>삭제</Button>
              <Button size="lg" autoFocus onClick={() => setConfirm(false)}>취소</Button>
            </div>
          </div>
        </Modal>
      )}
    </>
  );
}
