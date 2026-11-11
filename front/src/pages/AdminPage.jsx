// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Button, Card, DataTable, Pill, Tabs } from '../components/common/index';
import { ROUTES } from '../utils/routes';
import { useToast } from '../store/useToastStore';
import { usePost } from '../store/usePostStore';
import { useInquiry } from '../store/useInquiryStore';
import { useAllReports } from '../utils/useAllReports';
import { useWaitingCount } from '../store/useChatStore';
import { AccountLockManagement } from '../components/admin/AccountLockManagement';
import { getNoticesPage, getFaqsPage, getLibraryItems, createPost, updatePost as updatePostApi, deletePost } from '../api/post';
import { uploadFileChunks } from '../store/post-preview.js';

const ADMIN_TABS = [['members', '회원·게시판 관리'], ['support', '고객 지원']];
const PAGE_WINDOW_SIZE = 9; // 페이지 번호 버튼은 한 번에 최대 이만큼만 보여준다 (게시판·고객센터와 동일)
// 서버 공지는 숫자 postId, 브라우저에만 남아있는 예시 공지는 문자열 id를 쓴다. 다른 게시판 화면들과 같은 구분 방식.
const isServerId = (id) => /^\d+$/.test(id);

// 전체 페이지가 많아져도 버튼이 끝없이 늘어나지 않도록, 현재 페이지를 중심으로 최대 size개만 잘라 보여준다.
function getPageWindow(current, total, size) {
  if (total <= size) return Array.from({ length: total }, (_, i) => i + 1);
  const start = Math.min(Math.max(1, current - Math.floor(size / 2)), total - size + 1);
  return Array.from({ length: size }, (_, i) => start + i);
}

function Pager({ page, totalPages, onChange }) {
  if (totalPages <= 1) return null;
  const numbers = getPageWindow(page, totalPages, PAGE_WINDOW_SIZE);
  return (
    <div className="row" style={{ justifyContent: 'center', gap: 6, marginTop: 16 }}>
      <Button size="sm" variant="ghost" disabled={page <= 1} onClick={() => onChange(page - 1)}>이전</Button>
      {numbers.map((n) => (
        <Button key={n} size="sm" variant={n === page ? 'primary' : 'ghost'} onClick={() => onChange(n)}>{n}</Button>
      ))}
      <Button size="sm" variant="ghost" disabled={page >= totalPages} onClick={() => onChange(page + 1)}>다음</Button>
    </div>
  );
}

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

// 추가 버튼은 카드 헤더(aside)에 두고, 폼 자체는 이 컴포넌트가 열려 있을 때 본문에 그린다(공지·자료실과 같은 위치).
function FaqAddForm({ onAdd, onCancel }) {
  const [q, setQ] = useState('');
  const [a, setA] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const toast = useToast();

  const submit = async () => {
    if (isSubmitting) return;
    if (!q.trim() || !a.trim()) { toast('질문과 답변을 입력하세요'); return; }
    setIsSubmitting(true);
    try {
      await onAdd({ q, a });
      setQ(''); setA(''); onCancel();
    } catch {
      // 실패 토스트는 onAdd(상위)가 띄운다.
    } finally {
      setIsSubmitting(false);
    }
  };
  return (
    <div className="grid" style={{ gap: 8, marginTop: 12 }}>
      <input className="in" placeholder="질문" value={q} onChange={(e) => setQ(e.target.value)} disabled={isSubmitting} />
      <textarea className="in" rows={3} placeholder="답변" value={a} onChange={(e) => setA(e.target.value)} disabled={isSubmitting} />
      <div className="row">
        <Button size="sm" onClick={onCancel} disabled={isSubmitting}>취소</Button>
        <Button size="sm" variant="primary" onClick={submit} disabled={isSubmitting}>{isSubmitting ? '등록 중...' : '등록'}</Button>
      </div>
    </div>
  );
}

function LibraryRow({ item, onDelete }) {
  const fileUrl = item.attachments?.[0]?.previewUrl;
  return (
    <div className="row between" style={{ padding: '10px 0', borderBottom: '1px solid var(--border-soft)' }}>
      {fileUrl
        ? <a href={fileUrl} target="_blank" rel="noopener noreferrer">{item.title}</a>
        : <span>{item.title}</span>}
      <Button size="sm" variant="danger" onClick={onDelete}>삭제</Button>
    </div>
  );
}

// FaqAddForm과 같은 이유로, 추가 버튼은 카드 헤더(aside)에 두고 폼은 열려 있을 때 본문에 그린다.
function LibraryAddForm({ onAdd, onCancel }) {
  const [title, setTitle] = useState('');
  const [file, setFile] = useState(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const toast = useToast();

  const submit = async () => {
    if (isSubmitting) return;
    if (!title.trim() || !file) { toast('제목과 PDF 파일을 선택하세요'); return; }
    setIsSubmitting(true);
    try {
      await onAdd({ title, file });
      setTitle(''); setFile(null); onCancel();
    } catch {
      // 실패 토스트는 onAdd(상위)가 띄운다.
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="grid" style={{ gap: 8, marginTop: 12 }}>
      <input className="in" placeholder="자료 제목" value={title} onChange={(e) => setTitle(e.target.value)} disabled={isSubmitting} />
      <input
        className="in"
        type="file"
        accept=".pdf"
        disabled={isSubmitting}
        onChange={(e) => setFile(e.target.files[0] || null)}
      />
      <div className="row">
        <Button size="sm" onClick={onCancel} disabled={isSubmitting}>취소</Button>
        <Button size="sm" variant="primary" onClick={submit} disabled={isSubmitting}>{isSubmitting ? '업로드 중...' : '등록'}</Button>
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
  const { inquiries, loadInquiries } = useInquiry();
  const { reports } = useAllReports();
  const waitingCount = useWaitingCount();

  // 고객 지원 탭의 "Q&A 문의 처리" 요약 카드가 실제 DB 문의 수를 보여주도록 들어올 때마다 다시 받아온다.
  useEffect(() => { loadInquiries().catch(() => {}); }, [loadInquiries]);

  // "공지 관리"는 서버 공지를 한 페이지씩 받아온다(게시판·고객센터와 같은 방식).
  const [noticePage, setNoticePage] = useState(1);
  const [serverNotices, setServerNotices] = useState([]);
  const [noticeTotalPages, setNoticeTotalPages] = useState(1);
  const loadNotices = useCallback((page) => getNoticesPage({ page }).then(({ posts: items, meta }) => {
    setServerNotices(items);
    setNoticeTotalPages(meta.totalPages || 1);
  }).catch(() => {}), []);

  // FAQ는 관리자만 작성하는 DB 게시글이라 로컬 예시 데이터 없이 서버 목록만 한 페이지씩 보여준다.
  const [faqPage, setFaqPage] = useState(1);
  const [faqs, setFaqs] = useState([]);
  const [faqTotalPages, setFaqTotalPages] = useState(1);
  const loadFaqs = useCallback((page) => getFaqsPage({ page }).then(({ posts: items, meta }) => {
    const totalPages = meta.totalPages || 1;
    setFaqTotalPages(totalPages);
    // FAQ 삭제로 보던 페이지가 사라지면(예: 마지막 페이지의 유일한 FAQ를 지운 경우) 실제 마지막 페이지로 되돌린다.
    if (items.length === 0 && page > totalPages) {
      setFaqPage(Math.max(totalPages, 1));
      return;
    }
    setFaqs(items);
  }).catch(() => {}), []);

  // 자료실도 FAQ와 같은 이유로 로컬 예시 데이터 없이 서버 목록만 보여준다.
  const [libraryItems, setLibraryItems] = useState([]);
  const loadLibraryItems = () => getLibraryItems().then(setLibraryItems).catch(() => {});

  // FAQ·자료실 추가 버튼을 공지 관리와 같은 위치(카드 오른쪽 상단)에 두기 위해 폼 열림 상태를 여기서 관리한다.
  const [faqFormOpen, setFaqFormOpen] = useState(false);
  const [libraryFormOpen, setLibraryFormOpen] = useState(false);

  useEffect(() => { loadNotices(noticePage); }, [loadNotices, noticePage]);
  useEffect(() => { loadFaqs(faqPage); }, [loadFaqs, faqPage]);
  useEffect(() => { loadLibraryItems(); }, []);

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

  // FAQ 등록·수정·삭제는 전부 실제 게시글 API(postType=FAQ)로 처리하고, 끝나면 현재 페이지를 다시 받아온다.
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
      await loadFaqs(faqPage);
      toast('FAQ를 등록했습니다');
    } catch (error) {
      toast(error.message || 'FAQ 등록에 실패했습니다.');
      throw error;
    }
  };

  const saveFaq = async (faq, values) => {
    try {
      await updatePostApi(faq.id, makeFaqFormData(values));
      await loadFaqs(faqPage);
      toast('FAQ를 수정했습니다');
    } catch (error) {
      toast(error.message || 'FAQ 수정에 실패했습니다.');
      throw error;
    }
  };

  const deleteFaq = async (faq) => {
    try {
      await deletePost(faq.id);
      await loadFaqs(faqPage);
      toast('FAQ를 삭제했습니다');
    } catch (error) {
      toast(error.message || 'FAQ 삭제에 실패했습니다.');
    }
  };

  // 자료실 등록은 PDF를 청크 업로드(다른 게시글 첨부와 같은 엔드포인트)한 뒤, 그 결과 파일명을
  // 게시글 생성 API에 함께 보내 연결한다.
  const addLibraryItem = async ({ title, file }) => {
    try {
      const storedFileName = await uploadFileChunks(file);
      const formData = new FormData();
      formData.append('title', title);
      formData.append('content', title);
      formData.append('postType', 'LIBRARY');
      formData.append('isPublic', true);
      formData.append('savedFileNamesJson', JSON.stringify([storedFileName]));
      await createPost(formData);
      await loadLibraryItems();
      toast('자료를 등록했습니다');
    } catch (error) {
      toast(error.message || '자료 등록에 실패했습니다.');
      throw error;
    }
  };

  const deleteLibraryItem = async (item) => {
    try {
      await deletePost(item.id);
      await loadLibraryItems();
      toast('자료를 삭제했습니다');
    } catch (error) {
      toast(error.message || '자료 삭제에 실패했습니다.');
    }
  };

  const reportsPending = reports.filter((r) => r.status === '대기').length;
  const inquiriesPending = inquiries.filter((q) => q.status !== '답변 완료').length;
  // 서버 공지(현재 페이지) + 브라우저에만 남아있는 예시 공지(있다면, 1페이지에만)를 함께 보여준다.
  const notices = [...serverNotices, ...(noticePage === 1 ? posts.filter((p) => p.tab === '공지') : [])]
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt));

  return (
    <div className="grid">
      <Tabs tabs={ADMIN_TABS.map(([id, label]) => ({ id, label }))} active={tab} onChange={setTab} />

      {tab === 'members' && (
        <div className="grid">
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
          <Card title="1:1 채팅 상담" icon="messageSquare" aside={<Pill tone={waitingCount ? 'danger' : 'neutral'}>{waitingCount}건 대기</Pill>}>
            <p className="small muted">채팅 상담은 대기 목록에서 상담방을 직접 골라 응대하고, 지난 상담 기록도 같은 화면에서 확인합니다.</p>
            <div style={{ marginTop: 12 }}><Button size="sm" variant={waitingCount ? 'primary' : 'secondary'} to={ROUTES.adminChats}>상담 관리로 이동</Button></div>
          </Card>
          <AccountLockManagement />
        </div>
      )}

      {tab === 'support' && (
        <div className="grid">
          <Card title="Q&A 문의 처리" icon="messageSquare" aside={<Pill tone="warn">{inquiriesPending}건 대기</Pill>}>
            <DataTable columns={[
              { key: 'q', header: '문의', render: (q) => q.title },
              { key: 's', header: '상태', align: 'right', render: (q) => <Pill tone={q.status === '답변 완료' ? 'success' : 'warn'}>{q.status}</Pill> },
            ]} rows={[...inquiries].sort((a, b) => b.createdAt.localeCompare(a.createdAt)).slice(0, 5)} rowKey={(q) => q.id} />
            <p className="small muted" style={{ marginTop: 8 }}>답변 등록 시 사용자에게 알림이 전송됩니다.</p>
            <div style={{ marginTop: 12 }}><Button size="sm" to={`${ROUTES.adminInquiries}?tab=support`}>전체보기</Button></div>
          </Card>
          <Card title="공지 관리" icon="megaphone" aside={<Button size="sm" variant="soft" to={`${ROUTES.postWrite}?tab=${encodeURIComponent('공지')}`}>새 공지 추가</Button>}>
            {notices.length
              ? notices.map((p) => (
                <NoticeRow key={p.id} post={p} onSave={(v) => saveNotice(p, v)} />
              ))
              : <p className="state-msg">등록된 공지가 없습니다</p>}
            <Pager page={noticePage} totalPages={noticeTotalPages} onChange={setNoticePage} />
          </Card>
          <Card
            title="FAQ 관리"
            icon="fileText"
            aside={!faqFormOpen && <Button size="sm" variant="soft" onClick={() => setFaqFormOpen(true)}>새 FAQ 추가</Button>}
          >
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
            <Pager page={faqPage} totalPages={faqTotalPages} onChange={setFaqPage} />
            {faqFormOpen && <FaqAddForm onAdd={addFaq} onCancel={() => setFaqFormOpen(false)} />}
          </Card>
          <Card
            title="자료실 관리"
            icon="fileText"
            aside={!libraryFormOpen && <Button size="sm" variant="soft" onClick={() => setLibraryFormOpen(true)}>새 자료 추가</Button>}
          >
            {libraryItems.length
              ? libraryItems.map((item) => (
                <LibraryRow
                  key={item.id}
                  item={item}
                  onDelete={() => deleteLibraryItem(item)}
                />
              ))
              : <p className="state-msg">등록된 자료가 없습니다</p>}
            {libraryFormOpen && <LibraryAddForm onAdd={addLibraryItem} onCancel={() => setLibraryFormOpen(false)} />}
          </Card>
        </div>
      )}
    </div>
  );
}
