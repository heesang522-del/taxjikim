// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { Button, Card, DataTable, Pill } from '../components/common/index';
import { ROUTES } from '../utils/routes';
import { useReports } from '../utils/useReports';
import { usePost } from '../store/usePostStore';
import { useToast } from '../store/useToastStore';

const snippet = (text, n = 50) => (text.length > n ? `${text.slice(0, n)}…` : text);

export default function AdminReportsPage() {
  const { reports, resolveReport } = useReports();
  const { posts, comments, adminDeletePost, adminDeleteComment } = usePost();
  const toast = useToast();
  const pending = reports.filter((r) => r.status === '대기');
  const done = reports.filter((r) => r.status !== '대기');

  // 신고 대상의 현재 상태: 이미 지워졌거나 없는 경우도 구분한다
  const targetOf = (r) => {
    const post = posts.find((p) => p.id === r.postId);
    if (r.targetType === 'post') {
      if (!post) return { label: '(없는 글)', gone: true };
      if (post.deletedByAdmin) return { label: '관리자 삭제됨', gone: true };
      return { label: snippet(post.title), gone: false };
    }
    const c = comments.find((x) => x.id === r.commentId);
    if (!c) return { label: '(없는 댓글)', gone: true };
    if (c.deletedByAdmin) return { label: '관리자 삭제됨', gone: true };
    return { label: snippet(c.body), gone: false };
  };

  // 삭제: 대상 글·댓글에 "관리자에 의해 삭제됨" 흔적을 남기고 신고를 처리한다
  const remove = (r) => () => {
    if (r.targetType === 'post') adminDeletePost(r.postId); else adminDeleteComment(r.commentId);
    resolveReport(r.id, '삭제');
    toast(`${r.targetType === 'post' ? '게시글' : '댓글'}을(를) 삭제했습니다. "관리자에 의해 삭제됨"으로 표시됩니다`);
  };
  const dismiss = (r) => () => { resolveReport(r.id, '반려'); toast('신고를 반려했습니다'); };

  const columns = (withActions) => [
    { key: 't', header: '유형', render: (r) => <Pill>{r.targetType === 'post' ? '게시글' : '댓글'}</Pill> },
    { key: 'o', header: '신고 대상', render: (r) => {
      const t = targetOf(r);
      return r.targetType === 'post' && !t.gone
        ? <Button variant="link" size="sm" to={ROUTES.postDetail(r.postId)}>{t.label}</Button>
        : <span className={t.gone ? 'muted' : ''}>{t.label}</span>;
    } },
    { key: 'r', header: '사유', render: (r) => r.reason },
    { key: 'w', header: '신고자', render: (r) => r.reporter ?? '-' },
    withActions
      ? { key: 'a', header: '', align: 'right', render: (r) => (
        <div className="row" style={{ justifyContent: 'flex-end' }}>
          <Button size="sm" variant="danger" disabled={targetOf(r).gone} onClick={remove(r)}>삭제</Button>
          <Button size="sm" onClick={dismiss(r)}>반려</Button>
        </div>
      ) }
      : { key: 'a', header: '결과', align: 'right', render: (r) => <Pill tone={r.result === '삭제' ? 'danger' : 'success'}>{r.result ?? r.status}</Pill> },
  ];

  return (
    <div className="grid">
      <div className="row between">
        <h2 className="post-title">신고 접수</h2>
        <Button to={`${ROUTES.admin}?tab=members`}>관리자 홈으로</Button>
      </div>
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
    </div>
  );
}
