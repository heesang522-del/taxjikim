// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

export default function Toast({ message }) {
  if (!message) return null;
  return <div className="toast" role="status">{message}</div>;
}
