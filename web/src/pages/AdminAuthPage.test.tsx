import '@testing-library/jest-dom/vitest';
import {cleanup, render, screen} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {AdminAuthPage} from './AdminAuthPage';

describe('AdminAuthPage', () => {
  afterEach(cleanup);

  it('uses the shared remember-me wording', () => {
    render(<MemoryRouter><AdminAuthPage client={{signInAdmin: vi.fn()}}/></MemoryRouter>);
    expect(screen.getByLabelText('Remember me')).toBeInTheDocument();
    expect(screen.queryByText('Remember this browser')).not.toBeInTheDocument();
  });
});
