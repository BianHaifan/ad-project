import '@testing-library/jest-dom/vitest';
import {cleanup, render} from '@testing-library/react';
import {afterEach, describe, expect, it} from 'vitest';
import {RouteTitle} from './RouteTitle';

describe('RouteTitle', () => {
  afterEach(cleanup);

  it('keeps recruiter and administrator browser titles distinct', () => {
    const view = render(<RouteTitle title="HireX Recruiter"><div/></RouteTitle>);
    expect(document.title).toBe('HireX Recruiter');
    view.rerender(<RouteTitle title="HireX Administrator"><div/></RouteTitle>);
    expect(document.title).toBe('HireX Administrator');
  });
});
