import { useEffect, useState } from 'react'

// api 함수를 한 번 호출해서 { status, data }를 돌려주는 훅
// status: 'loading' | 'done' | 'error'
// fetcher는 src/api의 함수처럼 모듈 바깥에 있는(변하지 않는) 함수여야 한다
export function useApi(fetcher) {
  const [state, setState] = useState({ status: 'loading', data: null })

  useEffect(() => {
    // 화면을 떠난 뒤 응답이 도착해도 상태를 바꾸지 않기 위한 플래그
    let ignore = false
    fetcher()
      .then((data) => {
        if (!ignore) setState({ status: 'done', data })
      })
      .catch(() => {
        if (!ignore) setState({ status: 'error', data: null })
      })
    return () => {
      ignore = true
    }
  }, [fetcher])

  return state
}
