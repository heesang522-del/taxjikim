// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

/** 헤더·행을 받아 스크롤 가능한 표로 렌더링. columns: [{ key, header, align, render }] */
export default function DataTable({ columns, rows, rowKey, onRowClick }) {
  return (
    <div className="scroll">
      <table>
        <thead>
          <tr>{columns.map((c) => <th key={c.key} className={c.align === 'right' ? 'r' : ''}>{c.header}</th>)}</tr>
        </thead>
        <tbody>
          {rows.map((row, i) => (
            <tr key={rowKey ? rowKey(row) : i} className={onRowClick ? 'tbl-row' : ''} onClick={onRowClick ? () => onRowClick(row) : undefined}>
              {columns.map((c) => (
                <td key={c.key} className={`${c.align === 'right' ? 'r' : ''} ${c.num ? 'num' : ''}`}>{c.render ? c.render(row) : row[c.key]}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
