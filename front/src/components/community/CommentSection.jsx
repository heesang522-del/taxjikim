// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useEffect, useState } from 'react';
import { Button, Card } from '../common/index';
import { ROUTES } from '../../utils/routes';
import { useAuth } from '../../store/useAuthStore';
import { useBoard } from '../../store/useBoardStore';
import { useToast } from '../../store/useToastStore';
import { useReports } from '../../utils/useReports';
import ReportModal from './ReportModal';
import { getComments, createComment, deleteComment } from '../../api/comment';

export const DELETED_COMMENT = '관리자에 의해 삭제된 댓글입니다.';

const formatCommentTime = (value) => {
  if (!value) return '-';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' });
};

/** 글 상세 하단의 댓글 목록과 작성 폼. 회원은 작성·본인 댓글 삭제·타인 댓글 신고를 할 수 있다. */
export default function CommentSection({ postId, serverPost = false }) {
  const toast = useToast();
  const { loggedIn, user, accessToken } = useAuth();
  const { comments, addComment, removeComment } = useBoard();
  const { addReport, hasReported } = useReports();
  const [text, setText] = useState('');
  const [reporting, setReporting] = useState(null); // 신고하려는 댓글
  const [serverComments, setServerComments] = useState([]);
  const [loading, setLoading] = useState(serverPost);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  // 실제 게시글은 DB 댓글을 읽고, 기존 예시 게시글의 댓글은 브라우저에 유지한다.
  useEffect(() => {
    let active = true;
    if (serverPost) {
      getComments(postId)
        .then((items) => { if (active) setServerComments(items); })
        .catch((cause) => { if (active) setError(cause.message); })
        .finally(() => { if (active) setLoading(false); });
    }
    return () => { active = false; };
  }, [postId, serverPost]);
  const list = serverPost ? serverComments : comments.filter((c) => c.postId === postId);

  const submit = async (e) => {
    e.preventDefault();
    if (!text.trim()) { toast('댓글 내용을 입력하세요'); return; }
    if (saving || !loggedIn) return;
    setSaving(true);
    try {
      // 서버가 토큰으로 작성자 회원 번호를 확인하며, 클라이언트는 내용을 전송한다.
      if (serverPost) {
        await createComment(postId, text, accessToken);
        setText('');
        setServerComments(await getComments(postId));
      } else {
        addComment(postId, text);
        setText('');
      }
      setError('');
      toast('댓글을 등록했습니다');
    } catch (cause) {
      toast(cause.message);
    } finally {
      setSaving(false);
    }
  };

  const remove = async (commentId) => {
    if (saving) return;
    setSaving(true);
    try {
      if (serverPost) {
        await deleteComment(postId, commentId, accessToken);
        // 삭제 성공 후 화면에서만 제거하며 DB에는 삭제 시각과 기록이 남는다.
        setServerComments((items) => items.filter((comment) => comment.id !== commentId));
      } else removeComment(commentId);
      toast('댓글을 삭제했습니다');
    } catch (cause) {
      toast(cause.message);
    } finally {
      setSaving(false);
    }
  };

  const report = (reason) => {
    if (hasReported(user.nickname, { targetType: 'comment', postId, commentId: reporting.id })) {
      toast('이미 신고한 댓글입니다');
    } else {
      addReport({ targetType: 'comment', postId, commentId: reporting.id, reason, reporter: user.nickname });
      toast('신고를 접수했습니다');
    }
    setReporting(null);
  };

  return (
    <Card title={`댓글 ${list.length}`} icon="messageSquare">
      {loading && <p className="state-msg" role="status">댓글을 불러오는 중입니다.</p>}
      {error && <p className="state-msg" role="alert">{error}</p>}
      {!loading && !error && list.length === 0 && <p className="state-msg">첫 댓글을 남겨 보세요</p>}
      {list.map((c) => (
        <div key={c.id} style={{ paddingBlock: 10, borderTop: '1px solid var(--border)' }}>
          {c.deletedByAdmin ? (
            <p className="muted small">{DELETED_COMMENT}</p>
          ) : (
            <>
              <p className="small muted">
                {c.author} · 생성: {formatCommentTime(c.createdAt)}
                {c.updatedAt && ` · 수정: ${formatCommentTime(c.updatedAt)}`}
              </p>
              <p style={{ whiteSpace: 'pre-wrap' }}>{c.body}</p>
              {loggedIn && (
                <div className="row" style={{ marginTop: 6 }}>
                  {(serverPost ? String(c.userId) === String(user.userId) : c.author === user.nickname)
                    ? <Button size="sm" variant="ghost" disabled={saving} onClick={() => remove(c.id)}>삭제</Button>
                    : <Button size="sm" variant="ghost" onClick={() => setReporting(c)}>신고</Button>}
                </div>
              )}
            </>
          )}
        </div>
      ))}
      {loggedIn ? (
        <form className="grid" style={{ gap: 8, marginTop: 12 }} onSubmit={submit}>
          <textarea className="in" rows={3} maxLength={500} disabled={saving || loading} placeholder="댓글을 입력하세요" aria-label="댓글 내용" value={text} onChange={(e) => setText(e.target.value)} />
          <div><Button type="submit" variant="primary" size="sm" disabled={saving || loading}>{saving ? '처리 중...' : '댓글 등록'}</Button></div>
        </form>
      ) : (
        <p className="small muted" style={{ marginTop: 12 }}>댓글은 로그인 후 작성할 수 있습니다. <Button variant="link" size="sm" to={ROUTES.auth}>로그인</Button></p>
      )}
      {reporting && <ReportModal targetLabel="댓글" onSubmit={report} onClose={() => setReporting(null)} />}
    </Card>
  );
}
