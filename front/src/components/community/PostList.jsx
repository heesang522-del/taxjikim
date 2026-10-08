import PostItem from './PostItem'
import { Button, DividedList, Input, Row, StateMessage } from '../ui'

export default function PostList({ posts, keyword, onKeywordChange }) {
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
        <Button $variant="primary" $size="sm" onClick={() => alert('글쓰기 모달 창이 열립니다.')}>
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
