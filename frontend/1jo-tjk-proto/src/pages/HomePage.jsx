import HeroSection from '../components/home/HeroSection'
import StatsSection from '../components/home/StatsSection'
import PreviewListCard from '../components/home/PreviewListCard'
import AsyncBoundary from '../components/AsyncBoundary'
import MockBadge from '../components/MockBadge'
import { Badge, Grid, Row, Stack } from '../components/ui'
import { useApi } from '../utils/useApi'
import { getHomeData } from '../api/home'

export default function HomePage() {
  const { status, data } = useApi(getHomeData)

  return (
    <Stack $gap={32}>
      <HeroSection />
      <AsyncBoundary status={status}>
        <Stack $gap={24}>
          <Row $justify="flex-end">
            <MockBadge />
          </Row>
          <StatsSection stats={data?.stats ?? []} />
          <Grid $cols={2} $gap={24}>
            <PreviewListCard
              icon="megaphone"
              iconColor="var(--primary)"
              title="공지사항 및 주요 소식"
              items={data?.notices ?? []}
              to="/community"
              renderMeta={(n) => <span style={{ fontSize: 12, color: 'var(--text-faint)' }}>{n.date}</span>}
            />
            <PreviewListCard
              icon="flame"
              iconColor="var(--expense)"
              title="실시간 인기 정보 공유"
              items={data?.popularPosts ?? []}
              to="/community"
              renderMeta={(p) => <Badge>조회 {p.views.toLocaleString('ko-KR')}</Badge>}
            />
          </Grid>
        </Stack>
      </AsyncBoundary>
    </Stack>
  )
}
