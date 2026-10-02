import { useEffect, useState } from 'react'
import { getAdminStats } from '../api/admin'
import { Badge, Grid, PageHeader, Section, SectionHint, SectionTitle, StatTile } from '../components/ui'

const percent = (n) => `${(n * 100).toFixed(1)}%`

export default function AdminPage() {
  // status: 'loading' | 'error' | 'done'
  const [status, setStatus] = useState('loading')
  const [stats, setStats] = useState(null)

  useEffect(() => {
    // 화면을 떠난 뒤 응답이 도착해도 상태를 바꾸지 않도록 하는 플래그
    let ignore = false
    getAdminStats()
      .then((data) => {
        if (ignore) return
        setStats(data)
        setStatus('done')
      })
      .catch(() => {
        if (!ignore) setStatus('error')
      })
    return () => {
      ignore = true
    }
  }, [])

  // 응답은 왔지만 필요한 값이 없는 경우를 '빈 결과'로 취급
  const isEmpty = status === 'done' && !(stats?.model && stats?.data)

  return (
    <>
      <PageHeader>
        <h1>
          관리자 <Badge>예시 데이터</Badge>
        </h1>
        <p>모델 성능 지표와 데이터 현황입니다.</p>
      </PageHeader>

      {status === 'loading' && <p style={{ color: 'var(--text-muted)' }}>불러오는 중...</p>}
      {status === 'error' && (
        <p style={{ color: 'var(--expense)' }}>지표를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.</p>
      )}
      {isEmpty && <p style={{ color: 'var(--text-muted)' }}>표시할 지표가 없습니다.</p>}

      {status === 'done' && !isEmpty && (
        <>
          <Section>
            <SectionTitle>모델 성능</SectionTitle>
            <SectionHint>예시값입니다. 실제 모델 평가 결과가 아닙니다.</SectionHint>
            <Grid $cols={2}>
              <StatTile>
                <div className="label">정확도 (예시값)</div>
                <div className="value">{percent(stats.model.accuracy)}</div>
              </StatTile>
              <StatTile>
                <div className="label">재현율 (예시값)</div>
                <div className="value">{percent(stats.model.recall)}</div>
              </StatTile>
            </Grid>
          </Section>

          <Section>
            <SectionTitle>데이터 현황</SectionTitle>
            <Grid $cols={2}>
              <StatTile>
                <div className="label">거래 건수 (예시값)</div>
                <div className="value">{stats.data.transactionCount.toLocaleString('ko-KR')}건</div>
              </StatTile>
            </Grid>
          </Section>
        </>
      )}
    </>
  )
}
