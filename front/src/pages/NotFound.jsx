import { Link } from 'react-router-dom';

function NotFound() {
  return (
    <section style={{ padding: 40, textAlign: 'center' }}>
      <h2>페이지를 찾을 수 없습니다</h2>
      <Link to="/">홈으로 가기</Link>
    </section>
  );
}

export default NotFound;