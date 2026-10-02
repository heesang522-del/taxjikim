import { BrowserRouter, NavLink, Route, Routes } from 'react-router-dom'
import InputPage from './pages/InputPage'
import OutputPage from './pages/OutputPage'
import AdminPage from './pages/AdminPage'
import { Nav, Shell } from './components/ui'

function App() {
  return (
    <BrowserRouter>
      <Shell>
        <Nav>
          <span className="brand">크리에이터 세금 도우미</span>
          <div className="links">
            <NavLink to="/" end>
              입력
            </NavLink>
            <NavLink to="/output">출력</NavLink>
            <NavLink to="/admin">관리자</NavLink>
          </div>
        </Nav>
        <Routes>
          <Route path="/" element={<InputPage />} />
          <Route path="/output" element={<OutputPage />} />
          <Route path="/admin" element={<AdminPage />} />
        </Routes>
      </Shell>
    </BrowserRouter>
  )
}

export default App
