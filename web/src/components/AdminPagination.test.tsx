import '@testing-library/jest-dom/vitest';
import {cleanup, fireEvent, render, screen} from '@testing-library/react';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {AdminPagination} from './AdminPagination';

describe('AdminPagination', () => {
  afterEach(cleanup);

  it.each([
    [0, 'Showing 0–0 of 0', 'Page 0 of 0'],
    [1, 'Showing 1–1 of 1', 'Page 1 of 1'],
    [20, 'Showing 1–20 of 20', 'Page 1 of 1'],
    [21, 'Showing 1–20 of 21', 'Page 1 of 2'],
  ])('renders totals for %i rows', (total, range, pages) => {
    render(<AdminPagination page={1} total={total} pageSize={20} hasNext={total > 20} onPage={() => {}}/>);
    expect(screen.getByText(range)).toBeInTheDocument();
    expect(screen.getByText(pages)).toBeInTheDocument();
    expect(screen.getByText('20 per page')).toBeInTheDocument();
  });

  it('renders and navigates the last page', () => {
    const onPage = vi.fn();
    render(<AdminPagination page={2} total={21} pageSize={20} hasNext={false} onPage={onPage}/>);
    expect(screen.getByText('Showing 21–21 of 21')).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Next'})).toBeDisabled();
    fireEvent.click(screen.getByRole('button', {name: 'Previous'}));
    expect(onPage).toHaveBeenCalledWith(1);
  });
});
