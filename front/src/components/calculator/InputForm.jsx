import Icon from '../Icon'
import ProfileInputs from './ProfileInputs'
import FileUploader from './FileUploader'
import TransactionTable from './TransactionTable'
import { Button, Card, Row, Stack } from '../ui'

export default function InputForm({
  options,
  profile,
  onProfileChange,
  transactions,
  onAdd,
  onChange,
  onRemove,
  onLoadSample,
  onSubmit,
}) {
  return (
    <Card $pad={32}>
      <Stack $gap={24}>
        <Row $justify="space-between" $wrap>
          <div>
            <h2 style={{ fontSize: 20, fontWeight: 700 }}>프리랜서 세무 데이터 입력</h2>
            <p style={{ fontSize: 12, color: 'var(--text-muted)' }}>
              엑셀/CSV/PDF 파일을 업로드하거나 내역을 직접 입력하여 예상 세액을 계산하세요.
            </p>
          </div>
          <Button $variant="soft" $size="sm" onClick={onLoadSample}>
            테스트 샘플 자동입력
          </Button>
        </Row>

        <ProfileInputs profile={profile} onChange={onProfileChange} regions={options.regions} jobs={options.jobs} />
        <FileUploader />
        <TransactionTable
          transactions={transactions}
          categories={options.categories}
          onAdd={onAdd}
          onChange={onChange}
          onRemove={onRemove}
        />

        <Row $justify="flex-end" style={{ paddingTop: 16 }}>
          <Button $variant="primary" $size="lg" onClick={onSubmit} disabled={transactions.length === 0}>
            <Icon name="calculator" /> 계산하기 & 세액 산출 시작
          </Button>
        </Row>
      </Stack>
    </Card>
  )
}
