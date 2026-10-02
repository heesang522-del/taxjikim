import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTransactionStore } from '../store/useTransactionStore'
import { categorizeExpense } from '../utils/categorize'
import { CATEGORY_COLORS } from '../utils/categoryColors'
import {
  Badge,
  Button,
  Card,
  Field,
  Form,
  PageHeader,
  Section,
  SectionHint,
  SectionTitle,
  Table,
  TypeTag,
} from '../components/ui'

const emptyForm = { date: '', type: '수입', description: '', amount: '' }

export default function InputPage() {
  const navigate = useNavigate()
  const transactions = useTransactionStore((state) => state.transactions)
  const addTransaction = useTransactionStore((state) => state.addTransaction)
  const removeTransaction = useTransactionStore((state) => state.removeTransaction)
  const [form, setForm] = useState(emptyForm)

  function handleChange(e) {
    const { name, value } = e.target
    setForm((prev) => ({ ...prev, [name]: value }))
  }

  function handleSubmit(e) {
    e.preventDefault()
    if (!form.date || !form.description || !form.amount) return
    addTransaction({ ...form, amount: Number(form.amount) })
    setForm(emptyForm)
  }

  return (
    <>
      <PageHeader>
        <h1>거래 내역 입력</h1>
        <p>수입·지출 내역을 입력하면 지출은 카테고리로 자동 분류됩니다.</p>
      </PageHeader>

      <Section>
        <SectionTitle>거래 추가</SectionTitle>
        <Card>
          <Form onSubmit={handleSubmit}>
            <Field style={{ flex: '0 0 150px' }}>
              날짜
              <input type="date" name="date" value={form.date} onChange={handleChange} required />
            </Field>
            <Field style={{ flex: '0 0 110px' }}>
              수입/지출
              <select name="type" value={form.type} onChange={handleChange}>
                <option value="수입">수입</option>
                <option value="지출">지출</option>
              </select>
            </Field>
            <Field style={{ flex: '1 1 200px' }}>
              거래 내용
              <input
                type="text"
                name="description"
                value={form.description}
                onChange={handleChange}
                placeholder="예: 카메라 구매"
                required
              />
            </Field>
            <Field style={{ flex: '0 0 140px' }}>
              금액
              <input
                type="number"
                name="amount"
                value={form.amount}
                onChange={handleChange}
                placeholder="0"
                min="0"
                required
              />
            </Field>
            <Button type="submit" $variant="primary">
              추가
            </Button>
          </Form>
        </Card>
      </Section>

      <Section>
        <SectionTitle>거래 내역</SectionTitle>
        <SectionHint>
          지출 카테고리는 거래 내용 키워드 기반의 임시 분류이며, 추후 ML 모델로 대체될 예정입니다.
        </SectionHint>
        <Card style={{ padding: 0 }}>
          <Table>
            <thead>
              <tr>
                <th>날짜</th>
                <th>구분</th>
                <th>거래 내용</th>
                <th>카테고리</th>
                <th style={{ textAlign: 'right' }}>금액</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {transactions.map((tx) => {
                const category = tx.type === '지출' ? categorizeExpense(tx.description) : null
                return (
                  <tr key={tx.id}>
                    <td>{tx.date}</td>
                    <td>
                      <TypeTag $type={tx.type}>{tx.type}</TypeTag>
                    </td>
                    <td>{tx.description}</td>
                    <td>
                      {category && (
                        <Badge $bg={`${CATEGORY_COLORS[category]}26`} $fg={CATEGORY_COLORS[category]}>
                          {category}
                        </Badge>
                      )}
                    </td>
                    <td className="amount">{tx.amount.toLocaleString('ko-KR')}원</td>
                    <td>
                      <Button type="button" onClick={() => removeTransaction(tx.id)}>
                        삭제
                      </Button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </Table>
        </Card>
      </Section>

      <Button $variant="primary" onClick={() => navigate('/output')}>
        세금 분석 결과 보기
      </Button>
    </>
  )
}
