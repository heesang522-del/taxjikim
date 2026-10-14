// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { Button, Card, Icon, KeyValue } from '../components/common/index';
import { ROUTES } from '../utils/routes';
import { YEAR } from '../data/taxConstants';
import { man } from '../utils/format';
import { useTaxResult } from '../utils/useTaxResult';
import { useToast } from '../store/useToastStore';
import { useNavigate } from 'react-router-dom';

const STEPS = [
  ['1. 입력', '직접 입력하거나 PDF를 올립니다. 입금액만 넣으면 3.3% 원천징수는 자동으로 역산합니다.', 'uploadCloud'],
  ['2. 결과와 근거', '총수입에서 환급액까지 단계별 계산 과정을 보여주고, 월별 추이와 사업자 등록 전·후 비교를 함께 냅니다.', 'barChart'],
  ['3. 시뮬레이션', '추가 지출, 노란우산 납입 같은 가정값을 바꿔 환급액이 얼마나 변하는지 현재 결과와 나란히 봅니다.', 'sliders'],
];

export default function HomePage() {
  const c = useTaxResult();
  const navigate = useNavigate();
  const toast = useToast();
  return (
    <>
      <section className="hero-home grid g-side" style={{ gap: 32, paddingBlock: '28px 12px', alignItems: 'center' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
          <p className="eyebrow">프리랜서·콘텐츠 제작자용 종합소득세 시뮬레이터</p>
          <h1>올해 쓴 금액을 넣으면, 연말 환급액과 줄일 방법이 나옵니다.</h1>
          <p className="muted" style={{ maxWidth: '34em' }}>수입과 경비를 입력하면 연말까지 예상 지출을 더해 세액을 계산하고, 10~12월에 무엇을 하면 환급액이 얼마나 달라지는지 비교해 줍니다.</p>
          <div className="row">
            <Button variant="primary" size="lg" to={ROUTES.calcInput}>세금 계산하기</Button>
            <Button size="lg" onClick={() => { toast('샘플 데이터로 체험 중입니다'); navigate(ROUTES.calcInput); }}>가입 없이 샘플로 체험</Button>
          </div>
          <p className="small muted">세율·공제 한도는 예시값입니다. 신고 전 국세청 자료로 확인하세요.</p>
        </div>
        <Card>
          <p className="small muted">샘플 결과 · {YEAR}년 귀속</p>
          <p className="num" style={{ fontSize: 30, fontWeight: 600, marginBlock: 6, color: c.total < 0 ? 'var(--success)' : 'var(--warn)' }}>{c.total < 0 ? '환급 ' : '추가납부 '}{man(Math.abs(c.total))}</p>
          <KeyValue label="예상 총수입">{man(c.income)}</KeyValue>
          <KeyValue label="예상 필요경비">{man(c.expense)}</KeyValue>
          <KeyValue label="적용 세율">{Math.round(c.rate * 100)}%</KeyValue>
        </Card>
      </section>
      <section className="grid g3" style={{ marginTop: 28 }}>
        {STEPS.map(([t, d, ic]) => <Card key={t}><div className="icon-box" style={{ marginBottom: 16 }}><Icon name={ic} /></div><h3 style={{ fontSize: 16, marginBottom: 8 }}>{t}</h3><p className="muted" style={{ fontSize: 13 }}>{d}</p></Card>)}
      </section>
    </>
  );
}
