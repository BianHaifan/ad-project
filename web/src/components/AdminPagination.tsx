import {useEffect} from 'react';

export function AdminPagination({page, total, pageSize, hasNext, onPage}: {
  page: number; total: number; pageSize: number; hasNext: boolean; onPage: (page: number) => void;
}) {
  const totalPages = total === 0 ? 0 : Math.ceil(total / pageSize);
  const effectivePage = totalPages === 0 ? 1 : Math.min(page, totalPages);
  const start = total === 0 ? 0 : (effectivePage - 1) * pageSize + 1;
  const end = total === 0 ? 0 : Math.min(effectivePage * pageSize, total);

  useEffect(() => {
    if (totalPages > 0 && page > totalPages) onPage(totalPages);
    if (totalPages === 0 && page !== 1) onPage(1);
  }, [onPage, page, totalPages]);

  return <div className="admin-pagination">
    <span>Showing {start}–{end} of {total}</span>
    <button disabled={effectivePage <= 1} onClick={() => onPage(effectivePage - 1)}>Previous</button>
    <b>{effectivePage}</b>
    <small>Page {totalPages === 0 ? 0 : effectivePage} of {totalPages}</small>
    <button disabled={!hasNext || totalPages === 0 || effectivePage >= totalPages}
      onClick={() => onPage(effectivePage + 1)}>Next</button>
    <small>{pageSize} per page</small>
  </div>;
}
