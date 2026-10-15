import PostItem from './PostItem'
import { Button, DividedList, Input, Row, StateMessage } from '../ui'
import { useNavigate } from 'react-router-dom' // 1. useNavigate 임포트 추가

export default function PostList({ posts, keyword, onKeywordChange }) {
  const navigate = useNavigate(); // 2. 네비게이트 훅 선언

  return (
    <>
      <Row $justify="space-between">
        <Input
          $small
          type="text"
          value={keyword}
          onChange={(e) => onKeywordChange(e.target.value)}
          placeholder="검색어를 입력하세요..."
          style={{ maxWidth: 288 }}
        />
        {/* 3. alert 대신 /new-post 페이지로 이동하도록 수정 */}
        <Button 
          $variant="primary" 
          $size="sm" 
          onClick={() => navigate('/new-post')}
        >
          글쓰기
        </Button>
      </Row>
      {posts.length === 0 ? (
        <StateMessage>게시글이 없습니다.</StateMessage>
      ) : (
        <DividedList>
          {posts.map((post) => (
            <PostItem key={post.id} post={post} />
          ))}
        </DividedList>
      )}
    </>
  )
}