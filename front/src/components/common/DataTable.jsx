// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

/**
 * 헤더·행을 받아 스크롤 가능한 표로 렌더링.
 * columns: [{ key, header, align, render, width, ellipsis }]
 * - width: 열 폭을 고정한다(예: '70%', '90px'). 하나라도 지정되면 내용 길이와 무관하게 폭이 고정된다.
 * - ellipsis: 긴 내용을 한 줄로 유지하고 끝을 "…"로 자른다.
 */
export default function DataTable({ columns, rows, rowKey, onRowClick }) {
  const hasFixedWidth = columns.some((c) => c.width);
  return (
    <div className="scroll">
      <table style={hasFixedWidth ? { tableLayout: 'fixed' } : undefined}>
        {hasFixedWidth && (
          <colgroup>
            {columns.map((c) => <col key={c.key} style={c.width ? { width: c.width } : undefined} />)}
          </colgroup>
        )}
        <thead>
          <tr>{columns.map((c) => <th key={c.key} className={c.align === 'right' ? 'r' : ''}>{c.header}</th>)}</tr>
        </thead>
        <tbody>
          {rows.map((row, i) => (
            <tr key={rowKey ? rowKey(row) : i} className={onRowClick ? 'tbl-row' : ''} onClick={onRowClick ? () => onRowClick(row) : undefined}>
              {columns.map((c) => (
                <td
                  key={c.key}
                  className={`${c.align === 'right' ? 'r' : ''} ${c.num ? 'num' : ''}`}
                  style={c.ellipsis ? { overflow: 'hidden', whiteSpace: 'nowrap', textOverflow: 'ellipsis' } : undefined}
                >
                  {c.render ? c.render(row) : row[c.key]}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
