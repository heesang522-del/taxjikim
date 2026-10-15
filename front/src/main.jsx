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

// 이전 버전이 남긴 값만 삭제한다. 세션 스토리지에 새 값을 저장하거나 인증을 복원하지 않는다.
try {
  for (const key of ['taxjikim.auth-session.v1', 'taxlab.session.v1', 'taxlab.return-to.v1']) {
    sessionStorage.removeItem(key);
  }
} catch {
  // 저장소 접근이 차단되어 있어도 화면을 실행할 수 있게 한다.
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
