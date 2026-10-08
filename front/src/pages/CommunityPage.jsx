import { useState } from 'react'
import CommunityHeader from '../components/community/CommunityHeader'
import PostList from '../components/community/PostList'
import AsyncBoundary from '../components/AsyncBoundary'
import { Card, Stack } from '../components/ui'
import { useApi } from '../utils/useApi'
import { getCommunity } from '../api/community'

export default function CommunityPage() {
  const { status, data } = useApi(getCommunity)
  const [activeTab, setActiveTab] = useState('info')
  const [keyword, setKeyword] = useState('')

  // 선택한 게시판의 글만, 검색어가 제목에 들어간 것만 보여준다
  const posts = (data?.posts ?? []).filter(
    (p) => p.board === activeTab && p.title.includes(keyword.trim()),
  )

  return (
    <Stack $gap={24}>
      <AsyncBoundary status={status}>
        <CommunityHeader tabs={data?.tabs ?? []} activeTab={activeTab} onChangeTab={setActiveTab} />
        <Card>
          <Stack $gap={16}>
            <PostList posts={posts} keyword={keyword} onKeywordChange={setKeyword} />
          </Stack>
        </Card>
      </AsyncBoundary>
    </Stack>
  )
}
