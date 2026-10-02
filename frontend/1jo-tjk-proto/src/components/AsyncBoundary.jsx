import { StateMessage } from './ui'

// useApi의 status에 따라 로딩 / 오류 / 빈 결과 / 본문을 골라서 보여준다
export default function AsyncBoundary({ status, isEmpty = false, emptyText = '표시할 내용이 없습니다.', children }) {
  if (status === 'loading') return <StateMessage>불러오는 중...</StateMessage>
  if (status === 'error') return <StateMessage $error>불러오지 못했습니다. 잠시 후 다시 시도해 주세요.</StateMessage>
  if (isEmpty) return <StateMessage>{emptyText}</StateMessage>
  return children
}
