// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { Link } from 'react-router-dom';

/** variant: primary | soft | secondary | dark | danger | ghost | link, size: sm | md | lg.
 *  `to`는 앱 내 이동(Link), `href`는 외부 링크(a)로 렌더링한다. */
export default function Button({ variant = 'secondary', size = 'md', block, to, href, className = '', type = 'button', ...rest }) {
  const cls = ['btn', variant, size !== 'md' && size, block && 'block', className].filter(Boolean).join(' ');
  if (to) return <Link to={to} className={cls} {...rest} />;
  if (href) return <a href={href} target="_blank" rel="noopener noreferrer" className={cls} {...rest} />;
  return <button type={type} className={cls} {...rest} />;
}
