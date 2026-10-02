import FaqList from '../components/support/FaqList'
import InquiryForm from '../components/support/InquiryForm'
import AsyncBoundary from '../components/AsyncBoundary'
import MockBadge from '../components/MockBadge'
import { Card, Stack } from '../components/ui'
import { useApi } from '../utils/useApi'
import { getFaqs } from '../api/support'

export default function SupportPage() {
  const { status, data } = useApi(getFaqs)

  return (
    <Card $pad={32}>
      <Stack $gap={24}>
        <div style={{ textAlign: 'center', maxWidth: 512, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 8, alignItems: 'center' }}>
          <h2 style={{ fontSize: 24, fontWeight: 700 }}>
            고객센터 및 1:1 문의 <MockBadge />
          </h2>
          <p style={{ fontSize: 12, color: 'var(--text-muted)' }}>
            자주 묻는 질문을 확인하거나 1:1 문의를 남겨주세요. (운영시간: 오전 9시 ~ 저녁 6시)
          </p>
        </div>
        <AsyncBoundary status={status} isEmpty={data?.faqs.length === 0} emptyText="등록된 질문이 없습니다.">
          <FaqList faqs={data?.faqs ?? []} />
        </AsyncBoundary>
        <InquiryForm />
      </Stack>
    </Card>
  )
}
