// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useEffect, useState } from 'react';
import { Button, Card } from '../common/index';
import { ROUTES } from '../../utils/routes';
import { useAuth } from '../../store/useAuthStore';
import { useToast } from '../../store/useToastStore';
import { useReports } from '../../utils/useReports';
import ReportModal from './ReportModal';
import { getComments, createComment, updateComment, deleteComment } from '../../api/comment';

// 댓글 시각은 분 단위까지만 "00:00" 형식으로 표시한다.
const formatCommentTime = (value) => {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleTimeString('ko-KR', {
    timeZone: 'Asia/Seoul',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });
};

/** 글 상세 하단의 댓글 목록과 작성 폼. 회원은 작성·본인 댓글 수정·삭제·타인 댓글 신고를 할 수 있다. */
export default function CommentSection({ postId }) {
  const toast = useToast();
  const { loggedIn, user } = useAuth();
  const { addReport, hasReported } = useReports();
  const [text, setText] = useState('');
  const [reporting, setReporting] = useState(null); // 신고하려는 댓글
  const [replyingTo, setReplyingTo] = useState(null); // 답글 입력창을 연 댓글 id
  const [replyText, setReplyText] = useState('');
  const [editingId, setEditingId] = useState(null); // 수정 입력창을 연 댓글 id
  const [editText, setEditText] = useState('');
  const [list, setList] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    getComments(postId)
      .then((items) => {
        if (active) setList(items);
      })
      .catch((cause) => {
        if (active) setError(cause.message);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [postId]);
  // 삭제된 댓글(본인 삭제·관리자 삭제)은 답글 보존을 위해 자리만 남기므로 총 개수에서는 뺀다.
  const visibleCommentCount = list.filter((c) => !c.deleted).length;

  // 대댓글(1단계)만 지원하므로 부모 없는 댓글과, 부모 id별 답글 목록으로 나눠서 묶어 보여준다.
  // 최상위 댓글은 최신 작성 댓글이 가장 위에 보이도록 작성 시각 내림차순으로 정렬한다.
  const topLevelComments = list
    .filter((c) => !c.parentCommentId)
    .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
  const repliesByParent = list.reduce((map, c) => {
    if (c.parentCommentId) {
      (map[c.parentCommentId] ??= []).push(c);
    }
    return map;
  }, {});

  const isOwner = (c) => String(c.userId) === String(user.userId);

  const submit = async (e) => {
    e.preventDefault();
    if (!text.trim()) {
      toast('댓글 내용을 입력하세요');
      return;
    }
    if (saving || !loggedIn) return;
    setSaving(true);
    try {
      // 서버가 인증 쿠키로 작성자 회원 번호를 확인하며, 클라이언트는 내용을 전송한다.
      await createComment(postId, text);
      setText('');
      setList(await getComments(postId));
      setError('');
      toast('댓글을 등록했습니다');
    } catch (cause) {
      toast(cause.message);
    } finally {
      setSaving(false);
    }
  };

  const submitReply = async (e, parentId) => {
    e.preventDefault();
    if (!replyText.trim()) {
      toast('답글 내용을 입력하세요');
      return;
    }
    if (saving) return;
    setSaving(true);
    try {
      await createComment(postId, replyText, parentId);
      setReplyText('');
      setReplyingTo(null);
      setList(await getComments(postId));
      setError('');
      toast('답글을 등록했습니다');
    } catch (cause) {
      toast(cause.message);
    } finally {
      setSaving(false);
    }
  };

  const startEdit = (c) => {
    setEditingId(c.id);
    setEditText(c.body);
  };

  const cancelEdit = () => {
    setEditingId(null);
    setEditText('');
  };

  const submitEdit = async (e, c) => {
    e.preventDefault();
    if (!editText.trim()) {
      toast('댓글 내용을 입력하세요');
      return;
    }
    if (saving) return;
    setSaving(true);
    try {
      await updateComment(postId, c.id, editText);
      setList(await getComments(postId));
      cancelEdit();
      setError('');
      toast('댓글을 수정했습니다');
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
      await deleteComment(postId, commentId);
      // 로컬에서 바로 빼면 자식 답글이 있는지 모른 채 자리를 지워버린다.
      // 서버에서 다시 받아와야 답글 보존 로직(삭제 표시 + 답글 유지)이 그대로 적용된다.
      setList(await getComments(postId));
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

  // 댓글 한 개(최상위 또는 답글)를 그린다. 삭제된 댓글은 답글 보존을 위해 자리를 유지한 채 안내문만,
  // 수정 중이면 입력창을, 아니면 내용과 버튼을 보여준다.
  const renderComment = (c, { isReply = false } = {}) => (
    <div key={c.id} style={{ paddingBlock: 10, marginLeft: isReply ? 24 : 0, borderTop: '1px solid var(--border)' }}>
      {c.deleted ? (
        <p className="muted small">삭제된 댓글입니다.</p>
      ) : editingId === c.id ? (
        <form className="grid" style={{ gap: 8 }} onSubmit={(e) => submitEdit(e, c)}>
          <textarea
            className="in"
            rows={isReply ? 2 : 3}
            maxLength={500}
            disabled={saving}
            aria-label="댓글 수정 내용"
            value={editText}
            onChange={(e) => setEditText(e.target.value)}
          />
          <div className="row" style={{ gap: 8 }}>
            <Button type="submit" variant="primary" size="sm" disabled={saving}>
              {saving ? '처리 중...' : '저장'}
            </Button>
            <Button type="button" variant="ghost" size="sm" onClick={cancelEdit}>
              취소
            </Button>
          </div>
        </form>
      ) : (
        <>
          <p className="small muted">
            {isReply && '↳ '}
            {c.author} ·{' '}
            {c.updatedAt && c.updatedAt !== c.createdAt
              ? `(수정됨) ${formatCommentTime(c.updatedAt)}`
              : formatCommentTime(c.createdAt)}
          </p>
          <p style={{ whiteSpace: 'pre-wrap' }}>{c.body}</p>
          {loggedIn && (
            <div className="row" style={{ marginTop: 6, gap: 8 }}>
              {isOwner(c) ? (
                <>
                  <Button size="sm" variant="ghost" disabled={saving} onClick={() => startEdit(c)}>
                    수정
                  </Button>
                  <Button size="sm" variant="ghost" disabled={saving} onClick={() => remove(c.id)}>
                    삭제
                  </Button>
                </>
              ) : (
                <Button size="sm" variant="ghost" onClick={() => setReporting(c)}>
                  신고
                </Button>
              )}
              {!isReply && (
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={() => {
                    // 이미 열려 있으면 닫고, 아니면 이 댓글의 답글 입력창을 연다.
                    setReplyingTo((prev) => (prev === c.id ? null : c.id));
                    setReplyText('');
                  }}
                >
                  답글
                </Button>
              )}
            </div>
          )}
        </>
      )}
    </div>
  );

  return (
    <Card title={`댓글 ${visibleCommentCount}`} icon="messageSquare">
      {loading && (
        <p className="state-msg" role="status">
          댓글을 불러오는 중입니다.
        </p>
      )}
      {error && (
        <p className="state-msg" role="alert">
          {error}
        </p>
      )}
      {/* 댓글 작성 폼을 목록 상단에 고정해, 스크롤 없이 바로 작성할 수 있게 한다. */}
      {loggedIn ? (
        <form className="row" style={{ gap: 8, marginBottom: 12, alignItems: 'stretch' }} onSubmit={submit}>
          <textarea
            className="in"
            style={{ flex: 1 }}
            rows={3}
            maxLength={500}
            disabled={saving || loading}
            placeholder="댓글을 입력하세요"
            aria-label="댓글 내용"
            value={text}
            onChange={(e) => setText(e.target.value)}
          />
          <Button type="submit" variant="primary" size="sm" disabled={saving || loading}>
            {saving ? '처리 중...' : '댓글 등록'}
          </Button>
        </form>
      ) : (
        <p className="small muted" style={{ marginBottom: 12 }}>
          댓글은 로그인 후 작성할 수 있습니다.{' '}
          <Button variant="link" size="sm" to={ROUTES.auth}>
            로그인
          </Button>
        </p>
      )}
      {!loading && !error && list.length === 0 && <p className="state-msg">첫 댓글을 남겨 보세요</p>}
      {topLevelComments.map((c) => (
        <div key={c.id}>
          {renderComment(c)}
          {replyingTo === c.id && (
            <form
              className="grid"
              style={{ gap: 8, marginLeft: 24, marginBlock: 8 }}
              onSubmit={(e) => submitReply(e, c.id)}
            >
              <textarea
                className="in"
                rows={2}
                maxLength={500}
                disabled={saving}
                placeholder="답글을 입력하세요"
                aria-label="답글 내용"
                value={replyText}
                onChange={(e) => setReplyText(e.target.value)}
              />
              <div className="row" style={{ gap: 8 }}>
                <Button type="submit" variant="primary" size="sm" disabled={saving}>
                  {saving ? '처리 중...' : '답글 등록'}
                </Button>
                <Button type="button" variant="ghost" size="sm" onClick={() => setReplyingTo(null)}>
                  취소
                </Button>
              </div>
            </form>
          )}
          {(repliesByParent[c.id] ?? []).map((reply) => renderComment(reply, { isReply: true }))}
        </div>
      ))}
      {reporting && <ReportModal targetLabel="댓글" onSubmit={report} onClose={() => setReporting(null)} />}
    </Card>
  );
}
