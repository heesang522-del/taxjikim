import { useEffect, useState } from 'react';
import { getCompanies } from '../api/companyApi';

// 연결 확인용 화면. 종목 5개가 보이면 React → proxy → 백엔드 연결이 된 것이다.
function HomePage() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    getCompanies(1, 5)
      .then((res) => setItems(res.data.data))
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <p>불러오는 중…</p>;
  if (error) return <p>연결 실패: {error}</p>;

  return (
    <section>
      <h2>연결 확인</h2>
      <ul>
        {items.map((c) => (
          <li key={c.code}>
            {c.code} {c.name}
          </li>
        ))}
      </ul>
    </section>
  );
}

export default HomePage;