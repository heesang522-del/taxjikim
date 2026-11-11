// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { Card } from '../common/index';

/** 로그인 필요, 글 없음, 권한 없음처럼 한 가지 안내와 버튼만 보여주는 카드 */
export default function NoticeCard({ title, desc, action }) {
  return (
    <Card style={{ maxWidth: 480, margin: '40px auto', textAlign: 'center' }}>
      <h3>{title}</h3>
      <p className="muted" style={{ marginBottom: 14 }}>{desc}</p>
      {action}
    </Card>
  );
}
