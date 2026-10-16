// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Button, Card, DataTable, Pill, Tabs } from '../components/common/index';
import { BOARD_TABS, BOARD_WRITE_TABS } from '../data/content';
import { ROUTES } from '../utils/routes';
import { useBoard } from '../store/useBoardStore';
import { useAuth } from '../store/useAuthStore';
import { DELETED_POST } from '../data/options';

const CATEGORY_TONE = { 공지: 'danger', '정보 공유': 'primary', 팁: 'neutral' };
const SEARCH_SCOPES = [{ value: 'title', label: '제목' }, { value: 'author', label: '작성자' }, { value: 'body', label: '본문' }];

export default function BoardPage() {
  const [params] = useSearchParams();
  const [tab, setTab] = useState(BOARD_TABS.includes(params.get('tab')) ? params.get('tab') : BOARD_TABS[0]);
  const [scope, setScope] = useState('title');
  const [query, setQuery] = useState('');
  const navigate = useNavigate();
  const { posts, loadPosts } = useBoard();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  // 목록에 진입할 때마다 DB를 다시 읽어 신규 등록·삭제 결과를 반영한다.
  useEffect(() => {
    let active = true;
    loadPosts()
      .catch(() => { if (active) setError('게시글을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [loadPosts]);
  const { isAdmin } = useAuth();
  const all = tab === '전체';
  const q = query.trim().toLowerCase();
  const matchesQuery = (p) => {
    if (!q) return true;
    if (p.deletedByAdmin) return false;
    if (scope === 'author') return p.author.toLowerCase().includes(q);
    if (scope === 'body') return p.body.toLowerCase().includes(q);
    return p.title.toLowerCase().includes(q);
  };
  const rows = posts
    .filter((p) => (all || p.tab === tab) && matchesQuery(p))
    .sort((a, b) => {
      if (all) {
        const pin = (p) => (p.tab === '공지' ? 0 : 1);
        const d = pin(a) - pin(b);
        if (d) return d;
      }
      return b.createdAt.localeCompare(a.createdAt);
    });
  const columns = [
    { key: 't', header: '제목', render: (p) => (p.deletedByAdmin
      ? <span className="muted">{DELETED_POST}</span>
      : <><Pill tone={CATEGORY_TONE[p.tab]}>{p.tab}</Pill> {p.title}</>) },
    { key: 'w', header: '작성자', render: (p) => (p.deletedByAdmin ? '-' : p.author) },
    { key: 'v', header: '조회', align: 'right', num: true, render: (p) => (p.deletedByAdmin ? '-' : p.views.toLocaleString('ko-KR')) },
  ];
  const writeTab = all || (tab === '공지' && !isAdmin) ? BOARD_WRITE_TABS[0] : tab;
  return (
    <Card>
      <div className="row between" style={{ marginBottom: 16 }}>
        <Tabs tabs={BOARD_TABS.map((t) => ({ id: t, label: t }))} active={tab} onChange={setTab} />
        <div className="row" style={{ gap: 8 }}>
          <select className="in" style={{ width: 90 }} value={scope} onChange={(e) => setScope(e.target.value)} aria-label="검색 범위">
            {SEARCH_SCOPES.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
          </select>
          <input className="in" style={{ width: 200 }} type="search" placeholder="검색어를 입력하세요" value={query} onChange={(e) => setQuery(e.target.value)} aria-label="검색어" />
          <Button variant="soft" size="sm" to={`${ROUTES.boardWrite}?tab=${encodeURIComponent(writeTab)}`}>글쓰기</Button>
        </div>
      </div>
      {loading ? <p className="state-msg" role="status">게시글을 불러오는 중입니다.</p>
        : error ? <p className="state-msg" role="alert">{error}</p>
        : rows.length
        ? <DataTable columns={columns} rows={rows} rowKey={(p) => p.id} onRowClick={(p) => navigate(ROUTES.boardPost(p.id))} />
        : <p className="state-msg">{q ? '검색 결과가 없습니다' : '등록된 글이 없습니다'}</p>}
    </Card>
  );
}
