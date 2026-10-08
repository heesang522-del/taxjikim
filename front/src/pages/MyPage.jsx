import ProfileCard from '../components/mypage/ProfileCard'
import ProfileEditCard from '../components/mypage/ProfileEditCard'
import AnnualSummaryCard from '../components/mypage/AnnualSummaryCard'
import AsyncBoundary from '../components/AsyncBoundary'
import MockBadge from '../components/MockBadge'
import { Grid, Row, Stack } from '../components/ui'
import { useApi } from '../utils/useApi'
import { getProfile } from '../api/profile'

export default function MyPage() {
  const { status, data } = useApi(getProfile)

  return (
    <Stack $gap={24} $width={896}>
      <Row $justify="flex-end">
        <MockBadge />
      </Row>
      <AsyncBoundary status={status} isEmpty={!data}>
        {data && (
          <>
            <ProfileCard profile={data} />
            <Grid $cols={2} $gap={24}>
              <ProfileEditCard profile={data} />
              <AnnualSummaryCard yearly={data.yearly} />
            </Grid>
          </>
        )}
      </AsyncBoundary>
    </Stack>
  )
}
