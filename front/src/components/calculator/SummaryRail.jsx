// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { Button, Card, Icon, KeyValue } from '../common/index';
import { MONTH_NOW } from '../../data/taxConstants';
import { man } from '../../utils/format';
import { sumBy } from '../../utils/tax';
import { useTax } from '../../store/useTaxStore';

export default function SummaryRail({ onCalculate }) {
  const { rows, allRows, profile, inputMethod, evidenceDocuments } = useTax();
  const ti = sumBy(rows.filter((r) => r.kind === 'in'), (r) => r.amt);
  const te = sumBy(rows.filter((r) => r.kind === 'out'), (r) => r.amt);
  return (
    <Card as="aside" className="rail" title={`지금까지 입력 (1~${MONTH_NOW}월)`} icon="barChart">
      <p className="small muted" style={{ marginBottom: 12 }}>{inputMethod === 'file' ? 'PDF 내역 기준' : '직접 입력 내역 기준'}</p>
      <KeyValue label="수입 합계">{man(ti)}</KeyValue>
      <KeyValue label="경비 합계">{man(te)}</KeyValue>
      <KeyValue label="원천징수(3%)">{profile.auto33 ? man(ti * 0.03) : '-'}</KeyValue>
      <KeyValue label="순 사업소득" total>{man(ti - te)}</KeyValue>
      <p className="small muted" style={{ marginBlock: 12 }}>연말 전체 금액은 최근 6개월 평균으로 예측해서 계산합니다. 10~12월 실제 금액을 넣을수록 정확해집니다.</p>
      <Button variant="primary" size="lg" block onClick={onCalculate} disabled={rows.length === 0}><Icon name="calculator" /> {inputMethod === 'file' ? 'PDF 내역으로 계산' : '직접 입력 내역 분석'}</Button>
      {!rows.length && <p className="small muted" role="status" style={{ marginTop: 10 }}>{allRows.length
        ? '계산에 포함할 내역을 한 건 이상 선택해 주세요.'
        : inputMethod === 'file' ? evidenceDocuments.length
          ? '연말정산 증빙은 별도 분석 결과로 표시됩니다. 현재 버튼은 장부 거래 계산용이며 증빙 세액 계산은 아직 지원하지 않습니다.'
          : 'PDF에서 인식된 장부 거래가 없습니다. 계산할 내역이 없어 버튼을 사용할 수 없습니다.'
        : '내역을 한 건 이상 입력해 주세요.'}</p>}
    </Card>
  );
}
