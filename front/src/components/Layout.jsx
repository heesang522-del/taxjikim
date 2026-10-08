import { NavLink, Outlet } from 'react-router-dom';
import styled from 'styled-components';

const Header = styled.header`
  background: ${({ theme }) => theme.colors.white};
  border-bottom: 1px solid ${({ theme }) => theme.colors.border};
`;

const Nav = styled.nav`
  max-width: ${({ theme }) => theme.maxWidth};
  margin: 0 auto;
  padding: 16px;
  display: flex;
  gap: 24px;

  /* NavLink 는 현재 주소와 맞으면 active 클래스를 붙여 준다 */
  a.active {
    color: ${({ theme }) => theme.colors.primary};
    font-weight: 700;
  }
`;

const Main = styled.main`
  max-width: ${({ theme }) => theme.maxWidth};
  margin: 0 auto;
  padding: 24px 16px;
`;

function Layout() {
  return (
    <>
      <Header>
        <Nav>
          <NavLink to="/" end>
            홈
          </NavLink>
          <NavLink to="/companies">종목</NavLink>
          <NavLink to="/search">뉴스 검색</NavLink>
          <NavLink to="/predict">예측</NavLink>
        </Nav>
      </Header>
      <Main>
        <Outlet />
      </Main>
    </>
  );
}

export default Layout;