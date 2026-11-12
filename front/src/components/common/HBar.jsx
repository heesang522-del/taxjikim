// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

export default function HBar({ label, value, max = 100, display, scale = 1 }) {
  return (
    <div className="hbar">
      <span className="l">{label}</span>
      <div className="t"><i style={{ width: `${Math.min(100, (value / max) * 100 * scale)}%` }} /></div>
      <span className="n">{display ?? value}</span>
    </div>
  );
}
