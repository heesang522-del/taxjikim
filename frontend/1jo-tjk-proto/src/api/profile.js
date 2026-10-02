import data from '../mocks/profile.json'
import { delay } from './delay'

// MOCK: 실제 API로 교체할 때도 같은 시그니처(async, 반환 형태)를 유지한다
export async function getProfile() {
  await delay()
  return data
}
