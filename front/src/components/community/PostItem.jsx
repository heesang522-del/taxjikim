import styled from 'styled-components'
import { Badge } from '../ui'

const Item = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 8px;
  border-radius: 12px;
  font-size: 12px;
  cursor: pointer;

  &:hover {
    background: var(--bg);
  }
  .body {
    display: flex;
    flex-direction: column;
    gap: 4px;
    align-items: flex-start;
  }
  h4 {
    font-size: 14px;
    font-weight: 700;
  }
  .meta {
    color: var(--text-muted);
  }
  .time {
    color: var(--text-faint);
    white-space: nowrap;
  }
`

export default function PostItem({ post }) {
  return (
    <Item>
      <div className="body">
        <Badge $tone={post.tone}>{post.tag}</Badge>
        <h4>{post.title}</h4>
        <p className="meta">
          작성자: {post.author} | 조회수: {post.views.toLocaleString('ko-KR')} | 댓글: {post.comments}
        </p>
      </div>
      <span className="time">{post.time}</span>
    </Item>
  )
}
