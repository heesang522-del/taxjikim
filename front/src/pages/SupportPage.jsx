// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Button, Card, DataTable, Pill, Segmented, Tabs, TextAreaField, TextField } from '../components/common/index';
import { HELP_TABS } from '../data/content';
import { ROUTES } from '../utils/routes';
import { useToast } from '../stores/useToastStore';
import { useAuth } from '../stores/useAuthStore';
import { useInquiry } from '../stores/useInquiryStore';
import { toDateLabel } from '../utils/format';
import { createPost, getFaqsPage, getInquiriesPage, getLibraryItems } from '../api/post.js';

const VISIBILITY_OPTIONS = [{ value: 'public', label: '공개' }, { value: 'private', label: '비공개' }];
const PAGE_WINDOW_SIZE = 9; // 페이지 번호 버튼은 한 번에 최대 이만큼만 보여준다 (게시판과 동일, FAQ·Q&A 공용)

// 전체 페이지가 많아져도 버튼이 끝없이 늘어나지 않도록, 현재 페이지를 중심으로 최대 size개만 잘라 보여준다.
function getPageWindow(current, total, size) {
  if (total <= size) return Array.from({ length: total }, (_, i) => i + 1);
  const start = Math.min(Math.max(1, current - Math.floor(size / 2)), total - size + 1);
  return Array.from({ length: size }, (_, i) => start + i);
}

// 문의 목록에서 보여줄 수 있는 글만 남긴다: 공개 글은 모두, 비공개 글은 작성자와 관리자에게만.
function filterVisible(list, { isAdmin, loggedIn, user }) {
  return list.filter((q) => q.visibility === 'public' || isAdmin || (loggedIn && q.author === user.nickname));
}

function QnaForm({ onSubmit, onCancel, isSubmitting }) {
  const toast = useToast();
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [visibility, setVisibility] = useState('public');
  const [files, setFiles] = useState([]); // 프로토타입: 파일 자체는 저장하지 않고 이름만 남긴다 (파일 업로드 연동은 범위 밖)

  const submit = () => {
    if (isSubmitting) return;
    if (!title.trim() || !body.trim()) { toast('제목과 내용을 입력하세요'); return; }
    onSubmit({ title, body, visibility, files });
  };

  return (
    <Card>
      <div className="grid" style={{ gap: 12 }}>
        <TextField id="qTitle" label="제목" placeholder="제목을 입력하세요" maxLength={60} value={title} onChange={(e) => setTitle(e.target.value)} disabled={isSubmitting} />
        <TextAreaField id="qBody" label="문의 내용" rows={4} placeholder="문의 내용을 입력하세요" maxLength={2000} value={body} onChange={(e) => setBody(e.target.value)} disabled={isSubmitting} />
        <div className="field">
          <label htmlFor="qFiles">파일 첨부 (선택)</label>
          <input id="qFiles" className="in" type="file" multiple disabled={isSubmitting} onChange={(e) => setFiles([...e.target.files].map((f) => f.name))} />
          {files.length > 0 && <p className="small muted" style={{ marginTop: 6 }}>첨부: {files.join(', ')}</p>}
        </div>
        <div className="field">
          <label>공개 여부</label>
          <Segmented options={VISIBILITY_OPTIONS} value={visibility} onChange={setVisibility} label="공개 여부" />
          <p className="small muted" style={{ marginTop: 6 }}>비공개로 올리면 나와 관리자만 볼 수 있어요. 관리자 답변은 글을 열어서 확인합니다.</p>
        </div>
        <div className="row" style={{ justifyContent: 'flex-end' }}>
          <Button onClick={onCancel} disabled={isSubmitting}>취소</Button>
          <Button variant="primary" onClick={submit} disabled={isSubmitting}>{isSubmitting ? '등록 중...' : '등록'}</Button>
        </div>
      </div>
    </Card>
  );
}

const HELP_TAB_IDS = HELP_TABS.map(([id]) => id);

export default function HelpPage() {
  const [params, setParams] = useSearchParams();
  const [tab, setTab] = useState(HELP_TAB_IDS.includes(params.get('tab')) ? params.get('tab') : 'faq');
  const [writingQna, setWritingQna] = useState(false);
  const [isSubmittingQna, setIsSubmittingQna] = useState(false);
  const [qnaPage, setQnaPage] = useState(Math.max(Number(params.get('page')) || 1, 1));
  const [serverInquiries, setServerInquiries] = useState([]);
  const [qnaTotalPages, setQnaTotalPages] = useState(1);
  const [loadingInquiries, setLoadingInquiries] = useState(true);
  const [inquiriesError, setInquiriesError] = useState('');
  const [faqPage, setFaqPage] = useState(Math.max(Number(params.get('page')) || 1, 1));
  const [faqs, setFaqs] = useState([]);
  const [faqTotalPages, setFaqTotalPages] = useState(1);
  const [loadingFaqs, setLoadingFaqs] = useState(true);
  const [faqsError, setFaqsError] = useState('');
  const [libraryItems, setLibraryItems] = useState([]);
  const [loadingLibrary, setLoadingLibrary] = useState(true);
  const [libraryError, setLibraryError] = useState('');
  const toast = useToast();
  const navigate = useNavigate();
  const { loggedIn, user, isAdmin } = useAuth();
  const { inquiries } = useInquiry();

  // 문의 목록 1페이지를 서버에서 다시 받아온다. 탭 진입 시의 Effect와, 새 문의 등록 뒤의 즉시 갱신에서 함께 쓴다.
  const fetchQnaPage = useCallback(async (targetPage) => {
    const { posts: items, meta } = await getInquiriesPage({ page: targetPage });
    const totalPages = meta.totalPages || 1;
    setQnaTotalPages(totalPages);
    // 글이 삭제되거나 주소를 직접 고쳐서(예: ?page=99) 더 이상 없는 페이지를 가리키면, 실제 마지막 페이지로 되돌린다.
    if (items.length === 0 && targetPage > totalPages) {
      setQnaPage(Math.max(totalPages, 1));
      return;
    }
    setServerInquiries(items);
  }, []);

  // Q&A 탭에 들어오거나 페이지를 넘기면 그 페이지의 실제 문의만 서버에서 받아온다(게시판과 동일한 방식).
  useEffect(() => {
    if (tab !== 'qna') return undefined;
    let active = true;
    setLoadingInquiries(true);
    setInquiriesError('');
    fetchQnaPage(qnaPage)
      .catch(() => { if (active) setInquiriesError('문의 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'); })
      .finally(() => { if (active) setLoadingInquiries(false); });
    return () => { active = false; };
  }, [tab, qnaPage, fetchQnaPage]);

  // 탭·페이지를 주소 쿼리에 반영한다. 기본값(FAQ 탭/1페이지)은 생략해 주소를 깔끔하게 유지한다.
  useEffect(() => {
    const next = {};
    if (tab !== 'faq') next.tab = tab;
    if (tab === 'qna' && qnaPage !== 1) next.page = String(qnaPage);
    if (tab === 'faq' && faqPage !== 1) next.page = String(faqPage);
    setParams(next, { replace: true });
  }, [tab, qnaPage, faqPage, setParams]);

  // FAQ 목록 1페이지를 서버에서 받아온다. Q&A와 같은 방식(fetchQnaPage)으로 범위를 벗어난 페이지는 되돌린다.
  const fetchFaqPage = useCallback(async (targetPage) => {
    const { posts: items, meta } = await getFaqsPage({ page: targetPage });
    const totalPages = meta.totalPages || 1;
    setFaqTotalPages(totalPages);
    if (items.length === 0 && targetPage > totalPages) {
      setFaqPage(Math.max(totalPages, 1));
      return;
    }
    setFaqs(items);
  }, []);

  // FAQ 탭에 들어오거나 페이지를 넘기면 그 페이지의 FAQ만 서버에서 받아온다(Q&A와 동일한 방식).
  useEffect(() => {
    if (tab !== 'faq') return undefined;
    let active = true;
    setLoadingFaqs(true);
    setFaqsError('');
    fetchFaqPage(faqPage)
      .catch(() => { if (active) setFaqsError('FAQ를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'); })
      .finally(() => { if (active) setLoadingFaqs(false); });
    return () => { active = false; };
  }, [tab, faqPage, fetchFaqPage]);

  // 자료실 탭에 들어올 때 DB의 자료실 게시글(관리자가 올린 PDF)을 불러온다.
  useEffect(() => {
    if (tab !== 'lib') return undefined;
    let active = true;
    setLoadingLibrary(true);
    getLibraryItems()
      .then((list) => { if (active) setLibraryItems(list); })
      .catch(() => { if (active) setLibraryError('자료실 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'); })
      .finally(() => { if (active) setLoadingLibrary(false); });
    return () => { active = false; };
  }, [tab]);

  // 공개 글은 모두에게, 비공개 글은 작성자와 관리자에게만 보인다 (비회원·다른 회원에게는 목록에도 나오지 않는다)
  // 브라우저 예시 문의(로컬)는 페이지 단위로 조회되지 않으니, 게시판의 고정 공지와 같은 방식으로 1페이지에만 얹어 보여준다.
  const visibleLocalInquiries = filterVisible(inquiries.filter((q) => q.source !== 'server'), { isAdmin, loggedIn, user })
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  const visibleServerInquiries = filterVisible(serverInquiries, { isAdmin, loggedIn, user });
  const visibleInquiries = qnaPage === 1 ? [...visibleLocalInquiries, ...visibleServerInquiries] : visibleServerInquiries;
  const qnaPageNumbers = getPageWindow(qnaPage, qnaTotalPages, PAGE_WINDOW_SIZE);
  const faqPageNumbers = getPageWindow(faqPage, faqTotalPages, PAGE_WINDOW_SIZE);

  // 문의 등록은 실제 백엔드 QNA 게시글 생성 API로 보낸다. 생성 응답에 postId가 없어 등록 후 목록을 다시 불러온다.
  const submitQna = async ({ title, body, visibility }) => {
    if (isSubmittingQna) return;
    setIsSubmittingQna(true);
    try {
      const formData = new FormData();
      formData.append('title', title);
      formData.append('content', body);
      formData.append('postType', 'QNA');
      formData.append('isPublic', visibility === 'public');

      await createPost(formData);
      // 새 문의는 최신순 1페이지에 나타난다. 이미 1페이지라면 바로 다시 받아오고, 다른 페이지라면 1페이지로 옮긴다.
      if (qnaPage === 1) await fetchQnaPage(1);
      else setQnaPage(1);

      toast(visibility === 'private' ? '비공개로 등록했습니다. 나와 관리자만 볼 수 있어요' : '문의를 등록했습니다');
      setWritingQna(false);
    } catch (error) {
      toast(error.message || '문의 등록 중 오류가 발생했습니다.');
    } finally {
      setIsSubmittingQna(false);
    }
  };

  return (
    <Card>
      <div style={{ marginBottom: 16 }}><Tabs tabs={HELP_TABS.map(([id, label]) => ({ id, label }))} active={tab} onChange={setTab} /></div>
      {tab === 'faq' && (
        loadingFaqs ? <p className="state-msg" role="status">FAQ를 불러오는 중입니다.</p>
          : faqsError ? <p className="state-msg" role="alert">{faqsError}</p>
          : faqs.length
          ? (
            <>
              {faqs.map((f) => <details key={f.id}><summary>{f.q}</summary><p>{f.a}</p></details>)}
              {faqTotalPages > 1 && (
                <div className="row" style={{ justifyContent: 'center', gap: 6, marginTop: 16 }}>
                  <Button size="sm" variant="ghost" disabled={faqPage <= 1} onClick={() => setFaqPage((p) => p - 1)}>이전</Button>
                  {faqPageNumbers.map((n) => (
                    <Button key={n} size="sm" variant={n === faqPage ? 'primary' : 'ghost'} onClick={() => setFaqPage(n)}>{n}</Button>
                  ))}
                  <Button size="sm" variant="ghost" disabled={faqPage >= faqTotalPages} onClick={() => setFaqPage((p) => p + 1)}>다음</Button>
                </div>
              )}
            </>
          )
          : <p className="state-msg">등록된 FAQ가 없습니다</p>
      )}
      {tab === 'lib' && (
        loadingLibrary ? <p className="state-msg" role="status">자료실을 불러오는 중입니다.</p>
          : libraryError ? <p className="state-msg" role="alert">{libraryError}</p>
          : libraryItems.length
          ? (
            <DataTable
              columns={[
                { key: 'n', header: '자료', render: (x) => x.title },
                { key: 'd', header: '', align: 'right', render: () => <Pill tone="primary">다운로드</Pill> },
              ]}
              rows={libraryItems}
              rowKey={(x) => x.id}
              onRowClick={(x) => {
                const fileUrl = x.attachments?.[0]?.previewUrl;
                if (fileUrl) window.open(fileUrl, '_blank', 'noopener,noreferrer');
                else toast('첨부된 파일이 없습니다.');
              }}
            />
          )
          : <p className="state-msg">등록된 자료가 없습니다</p>
      )}
      {tab === 'qna' && (
        <div className="grid" style={{ gap: 12 }}>
          <div className="row between">
            <p className="small muted">궁금한 점을 올리면 다른 이용자도 볼 수 있어요. 개인 정보가 담긴 문의는 비공개로 올려주세요.</p>
            {loggedIn && !writingQna && <Button variant="soft" size="sm" onClick={() => setWritingQna(true)}>글쓰기</Button>}
            {!loggedIn && <Button variant="soft" size="sm" to={ROUTES.auth}>로그인 후 글쓰기</Button>}
          </div>
          {writingQna && (
            <QnaForm onSubmit={submitQna} onCancel={() => setWritingQna(false)} isSubmitting={isSubmittingQna} />
          )}
          {loadingInquiries ? <p className="state-msg" role="status">문의 목록을 불러오는 중입니다.</p>
            : inquiriesError ? <p className="state-msg" role="alert">{inquiriesError}</p>
            : visibleInquiries.length
            ? (
              <>
                <DataTable
                  // 제목 길이에 따라 작성자 칸이 움직이지 않도록 열 폭을 고정한다.
                  columns={[
                    { key: 't', header: '제목', width: '50%', ellipsis: true, render: (q) => <>{q.visibility === 'private' && <Pill>비공개</Pill>} {q.title}</> },
                    { key: 'a', header: '작성자', width: '20%', ellipsis: true, render: (q) => q.author },
                    { key: 'd', header: '등록일', width: '18%', render: (q) => toDateLabel(q.createdAt) },
                    { key: 's', header: '상태', width: '12%', align: 'right', render: (q) => <Pill tone={q.status === '답변 완료' ? 'success' : 'warn'}>{q.status}</Pill> },
                  ]}
                  rows={visibleInquiries}
                  rowKey={(q) => q.id}
                  onRowClick={(q) => navigate(ROUTES.qnaPost(q.id))}
                />
                {qnaTotalPages > 1 && (
                  <div className="row" style={{ justifyContent: 'center', gap: 6, marginTop: 16 }}>
                    <Button size="sm" variant="ghost" disabled={qnaPage <= 1} onClick={() => setQnaPage((p) => p - 1)}>이전</Button>
                    {qnaPageNumbers.map((n) => (
                      <Button key={n} size="sm" variant={n === qnaPage ? 'primary' : 'ghost'} onClick={() => setQnaPage(n)}>{n}</Button>
                    ))}
                    <Button size="sm" variant="ghost" disabled={qnaPage >= qnaTotalPages} onClick={() => setQnaPage((p) => p + 1)}>다음</Button>
                  </div>
                )}
              </>
            )
            : <p className="state-msg">등록된 문의가 없습니다</p>}
        </div>
      )}
    </Card>
  );
}
