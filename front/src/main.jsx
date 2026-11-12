import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { ThemeProvider } from 'styled-components';
import App from './App.jsx';
import GlobalStyle from './styles/GlobalStyle';
import theme from './styles/theme';
import './styles/tokens.css';
import './styles/global.css';
import './styles/layout.css';
import './styles/components.css';
import './styles/features.css';

// 이전 인증·mock 계정 기록만 제거하고 쿠키 인증으로 회원 정보를 다시 조회한다.
// 게시판·채팅·댓글·회원·알림·신고가 실제 백엔드 API로 바뀌면서 남은 구버전 mock 저장값도 함께 정리한다.
for (const storageName of ['localStorage', 'sessionStorage']) {
  try {
    for (const key of [
      'taxjikim.auth-session.v1',
      'taxlab.session.v1',
      'taxlab.return-to.v1',
      'taxlab.accounts.v1',
      'taxlab.login-lock.v1',
      'taxlab.presence.v1',
      'taxlab.board.v1',
      'taxlab.board.v2',
      'taxlab.chat.v1',
      'taxlab.comments.v1',
      'taxlab.members.v1',
      'taxlab.notif.seen.v1',
      'taxlab.reports.v1',
    ]) {
      window[storageName].removeItem(key);
    }
  } catch {
    // 저장소 접근이 차단되어 있어도 메모리와 쿠키로 화면을 실행할 수 있게 한다.
  }
}

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter>
      <ThemeProvider theme={theme}>
        <GlobalStyle />
        <App />
      </ThemeProvider>
    </BrowserRouter>
  </StrictMode>
);
