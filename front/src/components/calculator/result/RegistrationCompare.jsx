// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { Button, Card, DataTable, Icon, Pill } from '../../common/index';
import { ROUTES } from '../../../utils/routes';
import { signedMan } from '../../../utils/format';

export default function RegistrationCompare({ netNow, netSimple }) {
  const columns = [
    { key: 'k', header: '' },
    { key: 'a', header: '현재(미등록·3.3%)' },
    { key: 'b', header: '사업자 등록 후(장부 기장)' },
    { key: 'c', header: '단순경비율 적용 시' },
  ];
  const rows = [
    { k: '세액 결과', a: signedMan(netNow), b: `${signedMan(netNow)} (+부가세 별도)`, c: signedMan(netSimple) },
    { k: '장점', a: '절차 없음', b: '경비 증빙 시 전액 인정, 부가세 매입공제', c: '증빙 부담 적음' },
    { k: '단점', a: '경비 입증 서류 필요', b: '부가세 신고, 장부 작성', c: '소득이 클수록 불리, 대상 기준 확인 필요' },
  ];
  return (
    <Card title="사업자 등록 전·후 세금 비교" icon="scale" aside={<Pill tone="warn">예시값</Pill>}>
      <DataTable columns={columns} rows={rows} rowKey={(r) => r.k} />
      <div className="row" style={{ marginTop: 12 }}>
        <Button size="sm" variant="soft" href="https://www.hometax.go.kr">사업자 등록 안내 (홈택스) <Icon name="externalLink" size={14} /></Button>
        <Button size="sm" variant="soft" to={ROUTES.calcSim}>시뮬레이션 안내</Button>
      </div>
    </Card>
  );
}
