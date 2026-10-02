import SummaryCards from './SummaryCards'
import MonthlyChart from './MonthlyChart'
import RegistrationComparison from './RegistrationComparison'
import RelatedLinks from './RelatedLinks'
import { Grid, Hint, Stack } from '../ui'

export default function ResultView({ result, count, options, onRetry }) {
  return (
    <Stack $gap={24}>
      <Hint>기본공제 등 개인별 소득공제는 반영하지 않은 단순 추정치이며, 실제 세액은 신고 시 달라질 수 있습니다.</Hint>
      <SummaryCards result={result} count={count} />
      <MonthlyChart data={result.byMonth} />
      <Grid $cols={2} $gap={24}>
        <RegistrationComparison rows={options.comparisonRows} />
        <RelatedLinks links={options.guideLinks} onRetry={onRetry} />
      </Grid>
    </Stack>
  )
}
