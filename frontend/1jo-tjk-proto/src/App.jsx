import { BrowserRouter, Route, Routes } from 'react-router-dom'
import Layout from './components/layout/Layout'
import HomePage from './pages/HomePage'
import SignupPage from './pages/SignupPage'
import LoginPage from './pages/LoginPage'
import FindAccountPage from './pages/FindAccountPage'
import MyPage from './pages/MyPage'
import CalculatorPage from './pages/CalculatorPage'
import CommunityPage from './pages/CommunityPage'
import SupportPage from './pages/SupportPage'
import AdminPage from './pages/AdminPage'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<Layout />}>
          <Route path="/" element={<HomePage />} />
          <Route path="/signup" element={<SignupPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/find-account" element={<FindAccountPage />} />
          <Route path="/mypage" element={<MyPage />} />
          <Route path="/calculator" element={<CalculatorPage />} />
          <Route path="/community" element={<CommunityPage />} />
          <Route path="/support" element={<SupportPage />} />
          <Route path="/admin" element={<AdminPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

export default App
