import { useEffect, useState } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import Header from './Header'
import NotificationModal from './NotificationModal'
import { Shell } from '../ui'

// 모든 페이지 공통: 헤더 + 본문(Outlet) + 알림 모달
export default function Layout() {
  const { pathname } = useLocation()
  const [notificationOpen, setNotificationOpen] = useState(false)

  // 페이지를 옮기면 맨 위로 스크롤
  useEffect(() => {
    window.scrollTo({ top: 0 })
  }, [pathname])

  return (
    <>
      <Header onOpenNotifications={() => setNotificationOpen(true)} />
      <Shell>
        <Outlet />
      </Shell>
      <NotificationModal open={notificationOpen} onClose={() => setNotificationOpen(false)} />
    </>
  )
}
