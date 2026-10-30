// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Button, Card, DataTable, Pill, Tabs } from '../components/common/index';
import { BOARD_TABS, BOARD_WRITE_TABS } from '../data/content';
import { ROUTES } from '../utils/routes';
import { useAuth } from '../store/useAuthStore';
import { getBoardPostsPage } from '../api/post';

const CATEGORY_TONE = { 공지: 'danger', '정보 공유': 'primary', 팁: 'neutral' };

export default function BoardPage() {
  const [params] = useSearchParams();
  const [tab, setTab] = useState(BOARD_TABS.includes(params.get('tab')) ? params.get('tab') : BOARD_TABS[0]);
  const [queryInput, setQueryInput] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(1);
  const [posts, setPosts] = useState([]);
  const [totalPages, setTotalPages] = useState(1);
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const { isAdmin } = useAuth();

  // 검색어 입력마다 바로 서버에 요청하지 않도록 잠깐 멈췄다가 반영한다.
  useEffect(() => {
    const timer = setTimeout(() => setQuery(queryInput.trim()), 300);
    return () => clearTimeout(timer);
  }, [queryInput]);

  // 탭이나 검색어가 바뀌면 1페이지부터 다시 본다.
  useEffect(() => {
    setPage(1);
  }, [tab, query]);

  // 탭·검색어·페이지가 바뀔 때마다 그 페이지만 서버에서 받아온다.
  useEffect(() => {
    let active = true;
    setLoading(true);
    getBoardPostsPage({ page, boardType: tab === '전체' ? undefined : tab, keyword: query })
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
  }, [tab, query, page]);

  const columns = [
    { key: 't', header: '제목', render: (p) => <><Pill tone={CATEGORY_TONE[p.tab]}>{p.tab}</Pill> {p.title}</> },
    { key: 'w', header: '작성자', render: (p) => p.author },
    { key: 'v', header: '조회', align: 'right', num: true, render: (p) => p.views.toLocaleString('ko-KR') },
  ];
  const writeTab = tab === '전체' || (tab === '공지' && !isAdmin) ? BOARD_WRITE_TABS[0] : tab;
  const pageNumbers = Array.from({ length: totalPages }, (_, i) => i + 1);

  return (
    <Card>
      <div className="row between" style={{ marginBottom: 16 }}>
        <Tabs tabs={BOARD_TABS.map((t) => ({ id: t, label: t }))} active={tab} onChange={setTab} />
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
          <Button variant="soft" size="sm" to={`${ROUTES.boardWrite}?tab=${encodeURIComponent(writeTab)}`}>글쓰기</Button>
        </div>
      </div>
      {loading ? <p className="state-msg" role="status">게시글을 불러오는 중입니다.</p>
        : error ? <p className="state-msg" role="alert">{error}</p>
        : posts.length
        ? (
          <>
            <DataTable columns={columns} rows={posts} rowKey={(p) => p.id} onRowClick={(p) => navigate(ROUTES.boardPost(p.id))} />
            {pageNumbers.length > 1 && (
              <div className="row" style={{ justifyContent: 'center', gap: 6, marginTop: 16 }}>
                <Button size="sm" variant="ghost" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>이전</Button>
                {pageNumbers.map((n) => (
                  <Button key={n} size="sm" variant={n === page ? 'primary' : 'ghost'} onClick={() => setPage(n)}>{n}</Button>
                ))}
                <Button size="sm" variant="ghost" disabled={page >= pageNumbers.length} onClick={() => setPage((p) => p + 1)}>다음</Button>
              </div>
            )}
          </>
        )
        : <p className="state-msg">{query ? '검색 결과가 없습니다' : '등록된 글이 없습니다'}</p>}
    </Card>
  );
}
