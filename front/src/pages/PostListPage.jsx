// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Button, Card, DataTable, Pill, Tabs } from '../components/common/index';
import { POST_TABS, POST_WRITE_TABS } from '../data/content';
import { ROUTES } from '../utils/routes';
import { useAuth } from '../store/useAuthStore';
import { getPostsPage } from '../api/post';

const CATEGORY_TONE = { 공지: 'danger', '정보 공유': 'primary', 팁: 'neutral' };
const PINNED_NOTICE_COUNT = 3; // "전체" 탭 상단에 고정 노출할 최신 공지 개수
const PAGE_WINDOW_SIZE = 9; // 페이지 번호 버튼은 한 번에 최대 이만큼만 보여준다

// 전체 페이지가 많아져도 버튼이 끝없이 늘어나지 않도록, 현재 페이지를 중심으로 최대 size개만 잘라 보여준다.
// 양 끝(1페이지 근처·마지막 페이지 근처)에서는 윈도우를 안쪽으로 당겨 가능한 한 size개를 채운다.
function getPageWindow(current, total, size) {
  if (total <= size) return Array.from({ length: total }, (_, i) => i + 1);
  const start = Math.min(Math.max(1, current - Math.floor(size / 2)), total - size + 1);
  return Array.from({ length: size }, (_, i) => start + i);
}

export function PostListPage() {
  const [params, setParams] = useSearchParams();
  const [tab, setTab] = useState(POST_TABS.includes(params.get('tab')) ? params.get('tab') : POST_TABS[0]);
  const [queryInput, setQueryInput] = useState(params.get('q') ?? '');
  const [query, setQuery] = useState(params.get('q') ?? '');
  const [page, setPage] = useState(Number(params.get('page')) || 1);
  const [posts, setPosts] = useState([]);
  const [pinnedNotices, setPinnedNotices] = useState([]);
  const [totalPages, setTotalPages] = useState(1);
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const { isAdmin } = useAuth();
  // "전체" 탭이고 검색 중이 아닐 때만 공지를 상단에 고정 노출한다. 검색 중에는 검색 결과에만 집중하도록 숨긴다.
  const showPinned = tab === '전체' && !query;

  // 검색어 입력마다 바로 서버에 요청하지 않도록 잠깐 멈췄다가 반영한다.
  useEffect(() => {
    const timer = setTimeout(() => setQuery(queryInput.trim()), 300);
    return () => clearTimeout(timer);
  }, [queryInput]);

  // 탭이나 검색어가 실제로 바뀌면 1페이지부터 다시 본다. 직전 값과 비교해서 판단한다 — 단순히
  // "첫 렌더는 건너뛴다" 플래그를 쓰면 개발 모드의 StrictMode 이펙트 이중 실행에서 플래그가
  // 먼저 꺼져버려 마운트 직후(예: 상세 페이지에서 뒤로가기로 돌아왔을 때)에도 오탐으로 페이지가
  // 1로 리셋된다. 값 비교는 이중 실행돼도 매번 같은 결과라 안전하다.
  const prevFilters = useRef({ tab, query });
  useEffect(() => {
    if (prevFilters.current.tab !== tab || prevFilters.current.query !== query) {
      setPage(1);
    }
    prevFilters.current = { tab, query };
  }, [tab, query]);

  // 탭·검색어·페이지를 주소 쿼리에 반영한다. 기본값(전체/빈 검색어/1페이지)은 생략해 주소를 깔끔하게 유지한다.
  // 상세 페이지에서 뒤로가기로 돌아왔을 때 같은 화면을 보여주는 데 이 쿼리를 쓴다(새로고침·공유에도 유지됨).
  useEffect(() => {
    const next = {};
    if (tab !== POST_TABS[0]) next.tab = tab;
    if (query) next.q = query;
    if (page !== 1) next.page = String(page);
    setParams(next, { replace: true });
  }, [tab, query, page, setParams]);

  // 탭·검색어·페이지가 바뀔 때마다 그 페이지만 서버에서 받아온다.
  // showPinned이면 아래 일반 목록에서는 공지를 빼서, 위에 고정 노출한 공지와 중복되지 않게 한다.
  useEffect(() => {
    let active = true;
    setLoading(true);
    getPostsPage({ page, postType: tab === '전체' ? undefined : tab, keyword: query, excludeNotice: showPinned })
      .then(({ posts: items, meta }) => {
        if (!active) return;
        setPosts(items);
        setTotalPages(meta.totalPages || 1);
        setError('');
      })
      .catch(() => {
        if (active) setError('게시글을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [tab, query, page, showPinned]);

  // 상단에 고정 노출할 최신 공지를 따로 받아온다. "전체" 탭에서 검색 중이 아닐 때만 조회한다.
  // 보조 기능이라 실패해도 조용히 넘어가고, 일반 목록 조회에는 영향을 주지 않는다.
  useEffect(() => {
    if (!showPinned) {
      setPinnedNotices([]);
      return undefined;
    }
    let active = true;
    getPostsPage({ page: 1, postType: '공지', sort: 'latest' })
      .then(({ posts: items }) => {
        if (active) setPinnedNotices(items.slice(0, PINNED_NOTICE_COUNT));
      })
      .catch(() => {
        if (active) setPinnedNotices([]);
      });
    return () => {
      active = false;
    };
  }, [showPinned]);

  // 제목 길이가 들쭉날쭉해도 작성자 위치가 흔들리지 않도록 열 폭을 고정한다(제목 70% / 작성자 30%, 조회는 짧은 고정폭).
  const columns = [
    { key: 't', header: '제목', width: '70%', ellipsis: true, render: (p) => <><Pill tone={CATEGORY_TONE[p.tab]}>{p.tab}</Pill> {p.title}</> },
    { key: 'w', header: '작성자', width: '30%', ellipsis: true, render: (p) => p.author },
    { key: 'v', header: '조회', width: '90px', align: 'right', num: true, render: (p) => p.views.toLocaleString('ko-KR') },
  ];
  const writeTab = tab === '전체' || (tab === '공지' && !isAdmin) ? POST_WRITE_TABS[0] : tab;
  const pageNumbers = getPageWindow(page, totalPages, PAGE_WINDOW_SIZE);
  // 1페이지에서만 고정 공지를 맨 위에 끼워 넣는다. 별도 영역으로 나누지 않고 한 목록처럼 보이게 한다.
  // 2페이지부터는 공지 없이 일반 목록 그대로다.
  const displayRows = showPinned && page === 1 ? [...pinnedNotices, ...posts] : posts;

  return (
    <Card>
      {/* .between은 align-items: center라서, 탭 밑줄(12px 패딩+보더)만큼 더 큰 Tabs와 입력창·버튼이
          세로 중앙으로 맞춰져 검색창 쪽이 한 칸 내려와 보였다. 상단 기준으로 나란히 맞춘다. */}
      <div className="row between" style={{ marginBottom: 16, alignItems: 'flex-start' }}>
        <Tabs tabs={POST_TABS.map((t) => ({ id: t, label: t }))} active={tab} onChange={setTab} />
        <div className="row" style={{ gap: 8 }}>
          <input
            className="in"
            style={{ width: 220 }}
            type="search"
            placeholder="제목·내용 검색"
            value={queryInput}
            onChange={(e) => setQueryInput(e.target.value)}
            aria-label="검색어"
          />
          {/* size="sm"(패딩 6px)이면 검색창(.in, 패딩 10px)보다 눈에 띄게 작아 보여 기본 크기(md, 패딩 10px)로 맞췄다. */}
          <Button variant="soft" to={`${ROUTES.postWrite}?tab=${encodeURIComponent(writeTab)}`}>글쓰기</Button>
        </div>
      </div>
      {loading ? <p className="state-msg" role="status">게시글을 불러오는 중입니다.</p>
        : error ? <p className="state-msg" role="alert">{error}</p>
        : displayRows.length
        ? (
          <>
            <DataTable columns={columns} rows={displayRows} rowKey={(p) => p.id} onRowClick={(p) => navigate(ROUTES.postDetail(p.id), { state: { fromList: true } })} />
            {totalPages > 1 && (
              <div className="row" style={{ justifyContent: 'center', gap: 6, marginTop: 16 }}>
                <Button size="sm" variant="ghost" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>이전</Button>
                {pageNumbers.map((n) => (
                  <Button key={n} size="sm" variant={n === page ? 'primary' : 'ghost'} onClick={() => setPage(n)}>{n}</Button>
                ))}
                <Button size="sm" variant="ghost" disabled={page >= totalPages} onClick={() => setPage((p) => p + 1)}>다음</Button>
              </div>
            )}
          </>
        )
        : <p className="state-msg">{query ? '검색 결과가 없습니다' : '등록된 글이 없습니다'}</p>}
    </Card>
  );
}
