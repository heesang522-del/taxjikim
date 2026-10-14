// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

export function TextField({ id, label, className = '', inputClassName = '', ...rest }) {
  return (
    <div className={`field ${className}`}>
      <label htmlFor={id}>{label}</label>
      <input id={id} className={`in ${inputClassName}`} {...rest} />
    </div>
  );
}

export function SelectField({ id, label, options, className = '', ...rest }) {
  return (
    <div className={`field ${className}`}>
      <label htmlFor={id}>{label}</label>
      <select id={id} className="in" {...rest}>
        {options.map((o) => <option key={o}>{o}</option>)}
      </select>
    </div>
  );
}

export function TextAreaField({ id, label, ...rest }) {
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <textarea id={id} className="in" {...rest} />
    </div>
  );
}
