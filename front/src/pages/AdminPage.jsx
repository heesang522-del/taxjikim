// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Button, Card, DataTable, HBar, Pill, Tabs } from '../components/common/index';
import { ADMIN_STATS, USAGE_METRICS, FAILURE_LOG_SEED } from '../data/content';
import { ROUTES } from '../utils/routes';
import { useToast } from '../store/useToastStore';
import { usePost } from '../store/usePostStore';
import { useInquiry } from '../store/useInquiryStore';
import { useAllReports } from '../utils/useAllReports';
import { useWaitingCount } from '../store/useChatStore';
import { AccountLockManagement } from '../components/admin/AccountLockManagement';
import { toDateLabel } from '../utils/format';
import { getNotices, getFaqs, createPost, updatePost as updatePostApi, deletePost } from '../api/post';

const ADMIN_TABS = [['usage', '모델·처리 현황'], ['members', '회원·게시판 관리'], ['support', '고객 지원']];
// 서버 공지는 숫자 postId, 브라우저에만 남아있는 예시 공지는 문자열 id를 쓴다. 다른 게시판 화면들과 같은 구분 방식.
const isServerId = (id) => /^\d+$/.test(id);
const StatCard = ({ title, data }) => <Card title={title}>{data.map(([l, v]) => <HBar key={l} label={l} value={v} display={`${v}%`} scale={2} />)}</Card>;

function NoticeRow({ post, onSave }) {
  const [editing, setEditing] = useState(false);
  const [title, setTitle] = useState(post.title);
  const [body, setBody] = useState(post.body);
  const toast = useToast();

  if (!editing) {
    return (
      <details>
        <summary>{post.title}</summary>
        <p>{post.body}</p>
        <p className="small muted" style={{ marginTop: 6 }}>{post.author} · {post.createdAt}</p>
        <div className="row" style={{ marginTop: 8 }}>
          <Button size="sm" onClick={() => setEditing(true)}>수정</Button>
          <Button size="sm" variant="soft" to={ROUTES.postDetail(post.id)}>상세보기</Button>
        </div>
      </details>
    );
  }
  // onSave가 실제 서버 호출이라 실패할 수 있다. 실패하면(상위에서 토스트로 안내) 입력값을 유지한 채
  // 편집 모드를 그대로 둬서 다시 저장을 시도할 수 있게 한다.
  const save = async () => {
    if (!title.trim() || !body.trim()) { toast('제목과 내용을 입력하세요'); return; }
    try {
      await onSave({ title, body });
      setEditing(false);
    } catch {
      // 실패 토스트는 onSave(상위)가 띄운다.
    }
  };
  return (
    <div className="grid" style={{ gap: 8, padding: '10px 0', borderBottom: '1px solid var(--border-soft)' }}>
      <input className="in" value={title} onChange={(e) => setTitle(e.target.value)} />
      <textarea className="in" rows={3} value={body} onChange={(e) => setBody(e.target.value)} />
      <div className="row">
        <Button size="sm" onClick={() => setEditing(false)}>취소</Button>
        <Button size="sm" variant="primary" onClick={save}>저장</Button>
        <Button size="sm" variant="soft" to={ROUTES.postDetail(post.id)}>상세보기</Button>
      </div>
    </div>
  );
}

function FaqRow({ faq, onSave, onDelete }) {
  const [editing, setEditing] = useState(false);
  const [q, setQ] = useState(faq.q);
  const [a, setA] = useState(faq.a);
  const toast = useToast();

  if (!editing) {
    return (
      <details>
        <summary>{faq.q}</summary>
        <p>{faq.a}</p>
        <div className="row" style={{ marginTop: 8 }}>
          <Button size="sm" onClick={() => setEditing(true)}>수정</Button>
          <Button size="sm" variant="danger" onClick={onDelete}>삭제</Button>
        </div>
      </details>
    );
  }
  // onSave가 실제 서버 호출이라 실패할 수 있다. 실패하면(상위에서 토스트로 안내) 입력값을 유지한 채
  // 편집 모드를 그대로 둬서 다시 저장을 시도할 수 있게 한다.
  const save = async () => {
    if (!q.trim() || !a.trim()) { toast('질문과 답변을 입력하세요'); return; }
    try {
      await onSave({ q, a });
      setEditing(false);
    } catch {
      // 실패 토스트는 onSave(상위)가 띄운다.
    }
  };
  return (
    <div className="grid" style={{ gap: 8, padding: '10px 0', borderBottom: '1px solid var(--border-soft)' }}>
      <input className="in" placeholder="질문" value={q} onChange={(e) => setQ(e.target.value)} />
      <textarea className="in" rows={3} placeholder="답변" value={a} onChange={(e) => setA(e.target.value)} />
      <div className="row">
        <Button size="sm" onClick={() => setEditing(false)}>취소</Button>
        <Button size="sm" variant="primary" onClick={save}>저장</Button>
      </div>
    </div>
  );
}

function FaqAddForm({ onAdd }) {
  const [open, setOpen] = useState(false);
  const [q, setQ] = useState('');
  const [a, setA] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const toast = useToast();

  if (!open) return <Button size="sm" variant="soft" onClick={() => setOpen(true)}>새 FAQ 추가</Button>;
  const submit = async () => {
    if (isSubmitting) return;
    if (!q.trim() || !a.trim()) { toast('질문과 답변을 입력하세요'); return; }
    setIsSubmitting(true);
    try {
      await onAdd({ q, a });
      setQ(''); setA(''); setOpen(false);
    } catch {
      // 실패 토스트는 onAdd(상위)가 띄운다.
    } finally {
      setIsSubmitting(false);
    }
  };
  return (
    <div className="grid" style={{ gap: 8 }}>
      <input className="in" placeholder="질문" value={q} onChange={(e) => setQ(e.target.value)} disabled={isSubmitting} />
      <textarea className="in" rows={3} placeholder="답변" value={a} onChange={(e) => setA(e.target.value)} disabled={isSubmitting} />
      <div className="row">
        <Button size="sm" onClick={() => setOpen(false)} disabled={isSubmitting}>취소</Button>
        <Button size="sm" variant="primary" onClick={submit} disabled={isSubmitting}>{isSubmitting ? '등록 중...' : '등록'}</Button>
      </div>
    </div>
  );
}

export default function AdminPage() {
  const [params] = useSearchParams();
  const [tab, setTab] = useState(ADMIN_TABS.some(([id]) => id === params.get('tab')) ? params.get('tab') : ADMIN_TABS[0][0]);
  const toast = useToast();
  const navigate = useNavigate();

  const { posts, updatePost } = usePost();
  const { inquiries } = useInquiry();
  const { reports } = useAllReports();
  const waitingCount = useWaitingCount();

  // "공지 관리"는 서버 공지를 매번 DB에서 직접 받아온다(getPostsPage를 전체 페이지 모아 쓰는 getNotices).
  // 실패해도 조용히 넘어간다 — 로컬 예시 공지(있다면)만 보이는 정도이고, 탭을 다시 열면 재시도된다.
  const [serverNotices, setServerNotices] = useState([]);
  const loadNotices = () => getNotices().then(setServerNotices).catch(() => {});

  // FAQ는 관리자만 작성하는 DB 게시글이라 로컬 예시 데이터 없이 서버 목록만 보여준다.
  const [faqs, setFaqs] = useState([]);
  const loadFaqs = () => getFaqs().then(setFaqs).catch(() => {});

  useEffect(() => {
    loadNotices();
    loadFaqs();
  }, []);

  // 서버 공지는 실제 게시글 수정 API로 저장해야 DB에 반영된다. 브라우저에만 있는 예시 공지는
  // usePostStore의 로컬 수정으로 처리한다(화면에만 반영, 저장 공간이 있으면 localStorage에 남는다).
  // 사용자용 글 수정(PostForm)과 동일하게, 저장에 성공하면 고친 결과를 바로 확인하도록 그 글
  // 상세로 이동한다(관리자·일반 회원 공통 동작).
  const saveNotice = async (post, values) => {
    if (isServerId(post.id)) {
      try {
        const formData = new FormData();
        formData.append('title', values.title);
        formData.append('content', values.body);
        formData.append('postType', '공지');
        formData.append('isPublic', true);
        await updatePostApi(post.id, formData);
        toast('공지를 수정했습니다');
        navigate(ROUTES.postDetail(post.id));
      } catch (error) {
        toast(error.message || '공지 수정에 실패했습니다.');
        throw error;
      }
      return;
    }
    updatePost(post.id, { tab: '공지', title: values.title, body: values.body });
    toast('공지를 수정했습니다');
    navigate(ROUTES.postDetail(post.id));
  };

  // FAQ 등록·수정·삭제는 전부 실제 게시글 API(postType=FAQ)로 처리하고, 끝나면 목록을 다시 받아온다.
  const makeFaqFormData = (values) => {
    const formData = new FormData();
    formData.append('title', values.q);
    formData.append('content', values.a);
    formData.append('postType', 'FAQ');
    formData.append('isPublic', true);
    return formData;
  };

  const addFaq = async (values) => {
    try {
      await createPost(makeFaqFormData(values));
      await loadFaqs();
      toast('FAQ를 등록했습니다');
    } catch (error) {
      toast(error.message || 'FAQ 등록에 실패했습니다.');
      throw error;
    }
  };

  const saveFaq = async (faq, values) => {
    try {
      await updatePostApi(faq.id, makeFaqFormData(values));
      await loadFaqs();
      toast('FAQ를 수정했습니다');
    } catch (error) {
      toast(error.message || 'FAQ 수정에 실패했습니다.');
      throw error;
    }
  };

  const deleteFaq = async (faq) => {
    try {
      await deletePost(faq.id);
      await loadFaqs();
      toast('FAQ를 삭제했습니다');
    } catch (error) {
      toast(error.message || 'FAQ 삭제에 실패했습니다.');
    }
  };

  const reportsPending = reports.filter((r) => r.status === '대기').length;
  const inquiriesPending = inquiries.filter((q) => q.status !== '답변 완료').length;
  // 서버 공지(직접 조회) + 브라우저에만 남아있는 예시 공지(있다면)를 함께 보여준다.
  const notices = [...serverNotices, ...posts.filter((p) => p.tab === '공지')].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  const latestReports = [...reports].sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? '')).slice(0, 5);
  const pendingQna = inquiries
    .filter((q) => q.status !== '답변 완료')
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    .slice(0, 5);
  const totalRuns = USAGE_METRICS.calcRuns + USAGE_METRICS.uploadAttempts;
  const failureRate = (FAILURE_LOG_SEED.length / totalRuns * 100).toFixed(1);

  return (
    <div className="grid">
      <Tabs tabs={ADMIN_TABS.map(([id, label]) => ({ id, label }))} active={tab} onChange={setTab} />

      {tab === 'usage' && (
        <div className="grid">
          <Card title="상담 대기" icon="messageSquare" aside={<Pill tone={waitingCount ? 'danger' : 'neutral'}>{waitingCount}건 대기</Pill>}>
            <p className="small muted">{waitingCount ? `응대를 기다리는 채팅 상담이 ${waitingCount}건 있습니다.` : '응대 대기 중인 채팅 상담이 없습니다.'}</p>
            <div style={{ marginTop: 12 }}><Button size="sm" variant={waitingCount ? 'primary' : 'secondary'} to={ROUTES.adminChats}>상담 관리로 이동</Button></div>
          </Card>
          <div className="grid g2">
            <Card title="접수된 신고 (최신 5)" icon="shieldAlert" aside={<Pill tone="danger">{reportsPending}건 대기</Pill>}>
              {latestReports.length
                ? <DataTable columns={[
                  { key: 't', header: '유형', render: (r) => <Pill>{r.targetType === 'post' ? '게시글' : '댓글'}</Pill> },
                  { key: 'r', header: '사유', render: (r) => r.reason },
                  { key: 's', header: '상태', align: 'right', render: (r) => <Pill tone={r.status === '대기' ? 'warn' : 'success'}>{r.result ?? r.status}</Pill> },
                ]} rows={latestReports} rowKey={(r) => r.id} />
                : <p className="state-msg">접수된 신고가 없습니다</p>}
              <div style={{ marginTop: 12 }}><Button size="sm" to={`${ROUTES.adminReports}?tab=members`}>전체보기</Button></div>
            </Card>
            <Card title="Q&A 답변 대기 (최신 5)" icon="messageSquare" aside={<Pill tone="warn">{pendingQna.length}건</Pill>}>
              {pendingQna.length
                ? <DataTable columns={[
                  // 문의 제목 길이에 따라 작성자 칸이 움직이지 않도록 열 폭을 고정한다.
                  { key: 'q', header: '문의', width: '55%', ellipsis: true, render: (q) => <>{q.title} {q.visibility === 'private' && <Pill>비공개</Pill>}</> },
                  { key: 'a', header: '작성자', width: '25%', ellipsis: true, render: (q) => q.author },
                  { key: 'd', header: '등록일', width: '20%', align: 'right', render: (q) => toDateLabel(q.createdAt) },
                ]} rows={pendingQna} rowKey={(q) => q.id} />
                : <p className="state-msg">답변 대기 중인 Q&A가 없습니다</p>}
              <div style={{ marginTop: 12 }}><Button size="sm" to={`${ROUTES.adminInquiries}?tab=support`}>전체보기</Button></div>
            </Card>
          </div>
          <div className="grid g3">
            <Card className="stat">
              <p className="lbl">총 계산 실행</p>
              <p className="big">{USAGE_METRICS.calcRuns.toLocaleString()}회</p>
              <p className="sub">평균 처리 {USAGE_METRICS.avgCalcMs}ms</p>
            </Card>
            <Card className="stat">
              <p className="lbl">파일 업로드 시도</p>
              <p className="big">{USAGE_METRICS.uploadAttempts.toLocaleString()}건</p>
              <p className="sub">PDF 업로드 합계</p>
            </Card>
            <Card className="stat">
              <p className="lbl">실패율</p>
              <p className="big" style={{ color: 'var(--danger)' }}>{failureRate}%</p>
              <p className="sub">실패 {FAILURE_LOG_SEED.length}건 / 전체 {totalRuns.toLocaleString()}건</p>
            </Card>
          </div>
          <Card title="업로드·계산 실패 이력" icon="uploadCloud">
            <DataTable columns={[
              // 사유 길이에 따라 사용자 칸이 움직이지 않도록 열 폭을 고정한다.
              { key: 't', header: '종류', width: '15%', render: (f) => <Pill tone={f.type === '업로드' ? 'primary' : 'warn'}>{f.type}</Pill> },
              { key: 'r', header: '사유', width: '45%', ellipsis: true, render: (f) => f.reason },
              { key: 'u', header: '사용자', width: '20%', ellipsis: true, render: (f) => f.user },
              { key: 'd', header: '발생 시각', width: '20%', align: 'right', render: (f) => f.occurredAt },
            ]} rows={FAILURE_LOG_SEED} rowKey={(f) => f.id} />
          </Card>
        </div>
      )}

      {tab === 'members' && (
        <div className="grid">
          <div className="grid g3">
            <StatCard title="연령대별 가입자" data={ADMIN_STATS.age} />
            <StatCard title="소득 구간별" data={ADMIN_STATS.income} />
            <StatCard title="지역별" data={ADMIN_STATS.region} />
          </div>
          <AccountLockManagement />
          <Card title="신고 접수" icon="shieldAlert" aside={<Pill tone="danger">{reportsPending}건 대기</Pill>}>
            <DataTable columns={[
              { key: 't', header: '유형', render: (r) => <Pill>{r.targetType === 'post' ? '게시글' : '댓글'}</Pill> },
              { key: 'r', header: '사유', render: (r) => r.reason },
              { key: 'a', header: '', align: 'right', render: (r) => (r.status === '대기'
                ? <Pill tone="warn">대기</Pill>
                : <Pill tone={r.result === '삭제' ? 'danger' : 'success'}>{r.result ?? r.status}</Pill>) },
            ]} rows={reports.slice(0, 5)} rowKey={(r) => r.id} />
            <div style={{ marginTop: 12 }}><Button size="sm" to={`${ROUTES.adminReports}?tab=members`}>신고 처리하기</Button></div>
          </Card>
        </div>
      )}

      {tab === 'support' && (
        <div className="grid">
          <Card title="채팅 상담" icon="messageSquare" aside={<Pill tone={waitingCount ? 'danger' : 'neutral'}>{waitingCount}건 대기</Pill>}>
            <p className="small muted">채팅 상담은 대기 목록에서 상담방을 직접 골라 응대합니다.</p>
            <div style={{ marginTop: 12 }}><Button size="sm" to={ROUTES.adminChats}>상담 관리</Button></div>
          </Card>
          <Card title="Q&A 문의 처리" icon="messageSquare" aside={<Pill tone="warn">{inquiriesPending}건 대기</Pill>}>
            <DataTable columns={[
              { key: 'q', header: '문의', render: (q) => q.title },
              { key: 's', header: '상태', align: 'right', render: (q) => <Pill tone={q.status === '답변 완료' ? 'success' : 'warn'}>{q.status}</Pill> },
            ]} rows={[...inquiries].sort((a, b) => b.createdAt.localeCompare(a.createdAt)).slice(0, 5)} rowKey={(q) => q.id} />
            <p className="small muted" style={{ marginTop: 8 }}>답변 등록 시 사용자에게 알림이 전송됩니다.</p>
            <div style={{ marginTop: 12 }}><Button size="sm" to={`${ROUTES.adminInquiries}?tab=support`}>전체보기</Button></div>
          </Card>
          <Card title="공지 관리" icon="megaphone" aside={<Button size="sm" variant="soft" to={`${ROUTES.postWrite}?tab=${encodeURIComponent('공지')}`}>새 공지 올리기</Button>}>
            {notices.length
              ? notices.map((p) => (
                <NoticeRow key={p.id} post={p} onSave={(v) => saveNotice(p, v)} />
              ))
              : <p className="state-msg">등록된 공지가 없습니다</p>}
          </Card>
          <Card title="FAQ 관리" icon="fileText">
            {faqs.length
              ? faqs.map((f) => (
                <FaqRow
                  key={f.id}
                  faq={f}
                  onSave={(v) => saveFaq(f, v)}
                  onDelete={() => deleteFaq(f)}
                />
              ))
              : <p className="state-msg">등록된 FAQ가 없습니다</p>}
            <div style={{ marginTop: 12 }}>
              <FaqAddForm onAdd={addFaq} />
            </div>
          </Card>
        </div>
      )}
    </div>
  );
}
