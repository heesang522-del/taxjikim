import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import PostItem from './PostItem';
import { Button, Input, Row, StateMessage } from '../ui';

export default function PostList({ initialPosts = [] }) {
  const navigate = useNavigate();
  // API 호출 대신 props나 내부 상태로 관리
  const [savedPosts, setSavedPosts] = useState(initialPosts);
  const [keyword, setKeyword] = useState('');
  const [sort, setSort] = useState('latest');

  // 검색 및 정렬 필터링 계산
  const filteredPosts = savedPosts
    .filter((post) => `${post.title} ${post.content}`.toLowerCase().includes(keyword.trim().toLowerCase()))
    .sort((a, b) => {
      const dateA = a.createdAt || '';
      const dateB = b.createdAt || '';
      return sort === 'oldest' ? dateA.localeCompare(dateB) : dateB.localeCompare(dateA);
    });

  return (
    <div className="max-w-4xl mx-auto p-6 bg-white shadow-md rounded-lg mt-10">
      {/* 상단 타이틀 및 글쓰기 버튼 영역 */}
      <div className="flex justify-between items-center mb-6">
        <div>
          <h2 className="text-2xl font-bold text-gray-800">커뮤니티 게시판</h2>
          <p className="text-sm text-gray-500">나만의 게시물과 유용한 정보를 확인해보세요.</p>
        </div>
        <Button $variant="primary" $size="sm" onClick={() => navigate('/new-post')}>
          글쓰기
        </Button>
      </div>

      {/* 검색 및 정렬 바 */}
      <Row $justify="space-between" style={{ marginBottom: '20px', gap: '10px' }}>
        <Input
          $small
          type="text"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          placeholder="제목 또는 내용을 검색하세요..."
          style={{ flexGrow: 1, maxWidth: '350px' }}
        />
        <select
          className="border rounded px-3 py-1 text-sm bg-white"
          style={{ height: '38px' }}
          value={sort}
          onChange={(e) => setSort(e.target.value)}
        >
          <option value="latest">최신순</option>
          <option value="oldest">오래된순</option>
        </select>
      </Row>

      {/* 게시글 목록 렌더링 */}
      {filteredPosts.length === 0 ? (
        <StateMessage>
          <p className="text-muted mb-3">작성된 게시글이 없습니다.</p>
          <Button $variant="primary" $size="sm" onClick={() => navigate('/new-post')}>
            첫 게시글 쓰기
          </Button>
        </StateMessage>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {filteredPosts.map((post) => (
            <PostItem key={post.postId || post.id} post={post} />
          ))}
        </div>
      )}
    </div>
  );
}
