// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import Icon from './Icon';

/** title이 있으면 아이콘(선택)이 붙은 카드 제목을 그린다. aside는 제목 오른쪽 */
export default function Card({ title, icon, aside, as: Tag = 'div', className = '', children, ...rest }) {
  return (
    <Tag className={`card ${className}`} {...rest}>
      {title && (
        <h3 className="card-title">
          {icon && <Icon name={icon} size={16} />}
          {title}
          {aside && <span className="aside">{aside}</span>}
        </h3>
      )}
      {children}
    </Tag>
  );
}
