import adminStats from '../mocks/adminStats.json'

// 실제 API로 교체할 때도 같은 시그니처(async, 반환 형태)를 유지한다
const delay = (min = 300, max = 800) =>
  new Promise((resolve) => setTimeout(resolve, min + Math.random() * (max - min)))

// MOCK: 정확도·재현율·거래 건수는 고정된 예시값이다 (docs/to-verify.md 참고)
export async function getAdminStats() {
  await delay()
  return adminStats
}
