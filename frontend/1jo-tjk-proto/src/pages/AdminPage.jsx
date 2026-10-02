import AdminBanner from '../components/admin/AdminBanner'
import AdminStats from '../components/admin/AdminStats'
import UserManagement from '../components/admin/UserManagement'
import InquiryManagement from '../components/admin/InquiryManagement'
import AsyncBoundary from '../components/AsyncBoundary'
import MockBadge from '../components/MockBadge'
import { Grid, Row, Stack } from '../components/ui'
import { useApi } from '../utils/useApi'
import { getAdminStats } from '../api/admin'

export default function AdminPage() {
  const { status, data } = useApi(getAdminStats)
  // 응답은 왔지만 필요한 값이 없는 경우를 '빈 결과'로 취급
  const isEmpty = status === 'done' && !(data?.model && data?.data)

  return (
    <Stack $gap={24}>
      <AdminBanner />
      <Row $justify="flex-end">
        <MockBadge />
      </Row>
      <AsyncBoundary status={status} isEmpty={isEmpty} emptyText="표시할 지표가 없습니다.">
        {data && (
          <>
            <AdminStats model={data.model} data={data.data} ops={data.ops} />
            <Grid $cols={2} $gap={24}>
              <UserManagement users={data.users} />
              <InquiryManagement inquiry={data.inquiry} />
            </Grid>
          </>
        )}
      </AsyncBoundary>
    </Stack>
  )
}
