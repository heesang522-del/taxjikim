import Icon from '../Icon'
import { Button, Input, Row, Select } from '../ui'

export default function TransactionRow({ tx, categories, onChange, onRemove }) {
  const set = (key) => (e) => onChange(tx.id, { [key]: e.target.value })

  return (
    <tr>
      <td style={{ minWidth: 80 }}>
        <Select $small value={tx.type} onChange={set('type')}>
          <option>수입</option>
          <option>지출</option>
        </Select>
      </td>
      <td style={{ minWidth: 130 }}>
        <Input $small type="date" value={tx.date} onChange={set('date')} />
      </td>
      <td>
        <Row $gap={8}>
          <Input
            $small
            type="text"
            value={tx.description}
            onChange={set('description')}
            placeholder={tx.type === '수입' ? '수입 내용 입력' : '지출 내용 입력'}
            style={{ flex: 1, minWidth: 140 }}
          />
          <Select $small value={tx.category} onChange={set('category')} style={{ width: 'auto' }}>
            {categories.map((c) => (
              <option key={c}>{c}</option>
            ))}
          </Select>
        </Row>
      </td>
      <td style={{ minWidth: 110 }}>
        <Input $small type="number" min="0" value={tx.amount} onChange={set('amount')} placeholder="금액" />
      </td>
      <td>
        <Button $variant="danger" $size="sm" onClick={() => onRemove(tx.id)} aria-label="삭제" style={{ padding: 6 }}>
          <Icon name="trash" size={16} />
        </Button>
      </td>
    </tr>
  )
}
