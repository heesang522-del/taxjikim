import { client } from './client.js'

// 실제 알림 REST 요청은 기존 공통 client와 Vite /api 프록시를 거친다.
async function requestNotifications(config) {
  try {
    const response = await client.request(config)
    if (!response.data?.success) {
      const error = new Error(response.data?.error?.message ?? '요청을 처리하지 못했습니다.')
      error.code = response.data?.error?.code
      throw error
    }
    return response.data
  } catch (cause) {
    const detail = cause.response?.data?.error
    const error = new Error(
      detail?.message ??
        (cause.response
          ? '서버 오류가 발생했습니다.'
          : cause.isAxiosError
            ? '서버에 연결하지 못했습니다. 잠시 후 다시 시도해주세요.'
            : cause.message)
    )
    error.status = cause.response?.status ?? cause.status
    error.code = detail?.code ?? cause.code
    throw error
  }
}

// 로그인한 회원의 알림 목록 커서 페이징 조회 (최신순)
// cursorId: 이전 조회 결과의 meta.nextCursor 값. 없으면 최신 알림부터 조회한다.
// size: 한 번에 가져올 개수. 생략하면 서버 기본값(20)을 쓴다.
export function getNotifications({ cursorId, size } = {}) {
  return requestNotifications({ url: '/notifications', params: { cursorId, size } })
}

// 로그인한 회원의 미읽음 알림 수 조회 (뱃지용 폴링에 사용)
export function getUnreadNotificationCount() {
  return requestNotifications({ url: '/notifications/unread-count' })
}

// 로그인한 회원의 미읽음 알림을 전체 읽음 처리
export function markAllNotificationsRead() {
  return requestNotifications({ url: '/notifications/read-all', method: 'PATCH' })
}
