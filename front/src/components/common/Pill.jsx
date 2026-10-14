// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

/** tone: primary | success | danger | warn | neutral */
export default function Pill({ tone = 'neutral', children }) {
  return <span className={`pill ${tone}`}>{children}</span>;
}
