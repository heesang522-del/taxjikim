// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { Button, Card, DataTable, Pill } from '../components/common/index';
import { ROUTES } from '../utils/routes';
import { useAllReports } from '../utils/useAllReports';
import { resolveReport as resolveReportApi } from '../api/report';
import { usePost } from '../stores/usePostStore';
import { useToast } from '../stores/useToastStore';

const snippet = (text, n = 50) => (text.length > n ? `${text.slice(0, n)}…` : text);

export default function AdminReportsPage() {
  // 게시글·댓글 신고는 백엔드 API로 조회·처리한다. 브라우저에만 있는 예시 글 신고만 mock으로 남는다.
  const { reports, loading, loadError, reload: loadReports, resolveMockReport } = useAllReports();
  const { posts, adminDeletePost } = usePost();
  const toast = useToast();

  const pending = reports.filter((r) => r.status === '대기');
  const done = reports.filter((r) => r.status !== '대기');

  // 신고 대상의 현재 상태: 이미 지워졌거나 없는 경우도 구분한다
  const targetOf = (r) => {
    if (r.source === 'server') {
      if (r.targetType === 'comment') {
        if (!r.commentContent) return { label: '(없는 댓글)', gone: true };
        if (r.commentDeletedByType === 'ADMIN') return { label: '관리자 삭제됨', gone: true };
        if (r.commentDeletedAt) return { label: '삭제된 댓글', gone: true };
        return { label: snippet(r.commentContent), gone: false };
      }
      if (!r.postTitle) return { label: '(없는 글)', gone: true };
      if (r.postDeletedByType === 'ADMIN') return { label: '관리자 삭제됨', gone: true };
      if (r.postDeletedAt) return { label: '삭제된 글', gone: true };
      return { label: snippet(r.postTitle), gone: false };
    }
    // 브라우저에만 있는 예시 글 신고
    const post = posts.find((p) => p.id === r.postId);
    if (!post) return { label: '(없는 글)', gone: true };
    if (post.deletedByAdmin) return { label: '관리자 삭제됨', gone: true };
    return { label: snippet(post.title), gone: false };
  };

  // 신고된 글·댓글을 쓴 사람(피신고자). 신고자와 구분해서 보여준다.
  const authorOf = (r) => {
    if (r.source === 'server') return r.targetAuthor ?? '-';
    return posts.find((p) => p.id === r.postId)?.author ?? '-';
  };

  const remove = (r) => async () => {
    if (r.source === 'server') {
      try {
        await resolveReportApi(r.id, 'DELETE');
        toast(`${r.targetType === 'post' ? '게시글' : '댓글'}을(를) 삭제했습니다. "관리자에 의해 삭제됨"으로 표시됩니다`);
        loadReports();
      } catch (error) {
        toast(error.message || '처리에 실패했습니다.');
      }
      return;
    }
    adminDeletePost(r.postId);
    resolveMockReport(r.id, '삭제');
    toast('게시글을(를) 삭제했습니다. "관리자에 의해 삭제됨"으로 표시됩니다');
  };

  const dismiss = (r) => async () => {
    if (r.source === 'server') {
      try {
        await resolveReportApi(r.id, 'REJECT');
        toast('신고를 반려했습니다');
        loadReports();
      } catch (error) {
        toast(error.message || '처리에 실패했습니다.');
      }
      return;
    }
    resolveMockReport(r.id, '반려');
    toast('신고를 반려했습니다');
  };

  // 신고 대상·사유 길이에 따라 작성자·신고자 칸이 움직이지 않도록 열 폭을 고정한다.
  const columns = (withActions) => [
    { key: 't', header: '유형', width: '8%', render: (r) => <Pill>{r.targetType === 'post' ? '게시글' : '댓글'}</Pill> },
    { key: 'o', header: '신고 대상', width: '24%', ellipsis: true, render: (r) => {
      const t = targetOf(r);
      return r.targetType === 'post' && !t.gone
        ? <Button variant="link" size="sm" to={ROUTES.postDetail(r.postId)}>{t.label}</Button>
        : <span className={t.gone ? 'muted' : ''}>{t.label}</span>;
    } },
    { key: 'u', header: '작성자', width: '14%', ellipsis: true, render: (r) => authorOf(r) },
    { key: 'r', header: '사유', width: '22%', ellipsis: true, render: (r) => r.reason },
    { key: 'w', header: '신고자', width: '14%', ellipsis: true, render: (r) => r.reporter ?? '-' },
    withActions
      ? { key: 'a', header: '', width: '18%', align: 'right', render: (r) => (
        <div className="row" style={{ justifyContent: 'flex-end' }}>
          <Button size="sm" variant="danger" disabled={targetOf(r).gone} onClick={remove(r)}>삭제</Button>
          <Button size="sm" onClick={dismiss(r)}>반려</Button>
        </div>
      ) }
      : { key: 'a', header: '결과', width: '18%', align: 'right', render: (r) => <Pill tone={r.result === '삭제' ? 'danger' : 'success'}>{r.result ?? r.status}</Pill> },
  ];

  return (
    <div className="grid">
      <div className="row between">
        <h2 className="post-title">신고 접수</h2>
        <Button to={`${ROUTES.admin}?tab=members`}>관리자 홈으로</Button>
      </div>
      {loadError && <p role="alert" className="state-msg">{loadError}</p>}
      {loading
        ? <p role="status" className="state-msg">신고 목록을 불러오는 중입니다.</p>
        : (
          <>
            <Card title="대기중" icon="shieldAlert" aside={<Pill tone="danger">{pending.length}건</Pill>}>
              {pending.length
                ? <DataTable columns={columns(true)} rows={pending} rowKey={(r) => r.id} />
                : <p className="state-msg">대기 중인 신고가 없습니다</p>}
              <p className="small muted" style={{ marginTop: 8 }}>삭제하면 해당 글·댓글 자리에 "관리자에 의해 삭제되었다"는 표시가 남습니다. 문제가 없으면 반려하세요.</p>
            </Card>
            <Card title="처리됨" icon="shieldAlert" aside={<Pill tone="success">{done.length}건</Pill>}>
              {done.length
                ? <DataTable columns={columns(false)} rows={done} rowKey={(r) => r.id} />
                : <p className="state-msg">처리된 신고가 없습니다</p>}
            </Card>
          </>
        )}
    </div>
  );
}
