// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useState } from 'react';
import { Button, Card } from '../common/index';
import { ROUTES } from '../../utils/routes';
import { useAuth } from '../../store/useAuthStore';
import { useBoard } from '../../store/useBoardStore';
import { useToast } from '../../store/useToastStore';
import { useReports } from '../../utils/useReports';
import ReportModal from './ReportModal';

export const DELETED_COMMENT = '관리자에 의해 삭제된 댓글입니다.';

/** 글 상세 하단의 댓글 목록과 작성 폼. 회원은 작성·본인 댓글 삭제·타인 댓글 신고를 할 수 있다. */
export default function CommentSection({ postId }) {
  const toast = useToast();
  const { loggedIn, user } = useAuth();
  const { comments, addComment, removeComment } = useBoard();
  const { addReport, hasReported } = useReports();
  const [text, setText] = useState('');
  const [reporting, setReporting] = useState(null); // 신고하려는 댓글
  const list = comments.filter((c) => c.postId === postId);

  const submit = (e) => {
    e.preventDefault();
    if (!text.trim()) { toast('댓글 내용을 입력하세요'); return; }
    addComment(postId, text);
    setText('');
    toast('댓글을 등록했습니다');
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
      {list.length === 0 && <p className="state-msg">첫 댓글을 남겨 보세요</p>}
      {list.map((c) => (
        <div key={c.id} style={{ paddingBlock: 10, borderTop: '1px solid var(--border)' }}>
          {c.deletedByAdmin ? (
            <p className="muted small">{DELETED_COMMENT}</p>
          ) : (
            <>
              <p className="small muted">{c.author} · {c.createdAt}</p>
              <p style={{ whiteSpace: 'pre-wrap' }}>{c.body}</p>
              {loggedIn && (
                <div className="row" style={{ marginTop: 6 }}>
                  {c.author === user.nickname
                    ? <Button size="sm" variant="ghost" onClick={() => { removeComment(c.id); toast('댓글을 삭제했습니다'); }}>삭제</Button>
                    : <Button size="sm" variant="ghost" onClick={() => setReporting(c)}>신고</Button>}
                </div>
              )}
            </>
          )}
        </div>
      ))}
      {loggedIn ? (
        <form className="grid" style={{ gap: 8, marginTop: 12 }} onSubmit={submit}>
          <textarea className="in" rows={3} maxLength={500} placeholder="댓글을 입력하세요" aria-label="댓글 내용" value={text} onChange={(e) => setText(e.target.value)} />
          <div><Button type="submit" variant="primary" size="sm">댓글 등록</Button></div>
        </form>
      ) : (
        <p className="small muted" style={{ marginTop: 12 }}>댓글은 로그인 후 작성할 수 있습니다. <Button variant="link" size="sm" to={ROUTES.auth}>로그인</Button></p>
      )}
      {reporting && <ReportModal targetLabel="댓글" onSubmit={report} onClose={() => setReporting(null)} />}
    </Card>
  );
}
