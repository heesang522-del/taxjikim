// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { Navigate, Route, Routes } from 'react-router-dom';
import Layout from './components/layout/Layout';
import RequireAdmin from './components/layout/RequireAdmin';
import CalcLayout from './components/calculator/CalcLayout';
import HomePage from './pages/HomePage';
import AuthPage from './pages/AuthPage';
import MyPage from './pages/MyPage';
import CommunityPage from './pages/CommunityPage';
import CommunityPostPage from './pages/CommunityPostPage';
import CommunityWritePage from './pages/CommunityWritePage';
import CommunityEditPage from './pages/CommunityEditPage';
import SupportPage from './pages/SupportPage';
import SupportQnaPostPage from './pages/SupportQnaPostPage';
import BlockedPage from './pages/BlockedPage';
import AdminPage from './pages/AdminPage';
import AdminReportsPage from './pages/AdminReportsPage';
import AdminInquiriesPage from './pages/AdminInquiriesPage';
import AdminChatsPage from './pages/AdminChatsPage';
import CalculatorInputPage from './pages/CalculatorInputPage';
import CalculatorLoadingPage from './pages/CalculatorLoadingPage';
import CalculatorResultPage from './pages/CalculatorResultPage';
import CalculatorSimPage from './pages/CalculatorSimPage';

// 주소에 맞는 화면을 연결한다. BrowserRouter는 main.jsx에서 한 번만 적용한다.
// 로그인·회원가입·계정 찾기는 같은 AuthPage를 쓰고, key로 주소가 바뀔 때 탭을 새로 시작한다.
function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<HomePage />} />
        <Route path="calculator" element={<CalcLayout />}>
          <Route index element={<Navigate to="input" replace />} />
          <Route path="input" element={<CalculatorInputPage />} />
          <Route path="loading" element={<CalculatorLoadingPage />} />
          <Route path="result" element={<CalculatorResultPage />} />
          <Route path="sim" element={<CalculatorSimPage />} />
        </Route>
        <Route path="login" element={<AuthPage key="login" />} />
        <Route path="signup" element={<AuthPage key="signup" />} />
        <Route path="find-account" element={<AuthPage key="find-account" />} />
        <Route path="blocked" element={<BlockedPage />} />
        <Route path="mypage" element={<MyPage />} />
        <Route path="community" element={<CommunityPage />} />
        <Route path="community/new" element={<CommunityWritePage />} />
        <Route path="community/:id" element={<CommunityPostPage />} />
        <Route path="community/:id/edit" element={<CommunityEditPage />} />
        <Route path="support" element={<SupportPage />} />
        <Route path="support/qna/:id" element={<SupportQnaPostPage />} />
        <Route path="admin" element={<RequireAdmin><AdminPage /></RequireAdmin>} />
        <Route path="admin/reports" element={<RequireAdmin><AdminReportsPage /></RequireAdmin>} />
        <Route path="admin/inquiries" element={<RequireAdmin><AdminInquiriesPage /></RequireAdmin>} />
        <Route path="admin/chats" element={<RequireAdmin><AdminChatsPage /></RequireAdmin>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}

export default App;
