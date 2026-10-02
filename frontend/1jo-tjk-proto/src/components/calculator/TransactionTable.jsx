import TransactionRow from './TransactionRow'
import { Button, Row, StateMessage, Stack, Table } from '../ui'

export default function TransactionTable({ transactions, categories, onAdd, onChange, onRemove }) {
  return (
    <Stack $gap={16} style={{ paddingTop: 16, borderTop: '1px solid var(--border-soft)' }}>
      <Row $justify="space-between">
        <h3 style={{ fontSize: 14, fontWeight: 700 }}>지출 및 수입 내역 직접 입력</h3>
        <Button $variant="soft" $size="sm" onClick={onAdd}>
          + 내역 추가하기
        </Button>
      </Row>
      {transactions.length === 0 ? (
        <StateMessage>입력된 거래 내역이 없습니다. '내역 추가하기'를 눌러 입력하세요.</StateMessage>
      ) : (
        <div style={{ overflowX: 'auto' }}>
          <Table>
            <thead>
              <tr>
                <th>구분</th>
                <th>날짜</th>
                <th>내용 / 카테고리</th>
                <th>금액 (원)</th>
                <th>삭제</th>
              </tr>
            </thead>
            <tbody>
              {transactions.map((tx) => (
                <TransactionRow key={tx.id} tx={tx} categories={categories} onChange={onChange} onRemove={onRemove} />
              ))}
            </tbody>
          </Table>
        </div>
      )}
    </Stack>
  )
}
