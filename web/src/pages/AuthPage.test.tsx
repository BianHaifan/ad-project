import '@testing-library/jest-dom/vitest';
import {cleanup, fireEvent, render, screen, waitFor} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {AuthApiError, type AuthClient} from '../api/authClient';
import {AuthPage} from './AuthPage';

type AuthPageClient = Pick<AuthClient, 'signIn' | 'register'>;

function renderSignIn(client: AuthPageClient) {
  render(<MemoryRouter><AuthPage mode="signin" client={client}/></MemoryRouter>);
  fireEvent.change(screen.getByLabelText('WORK EMAIL'), {target: {value: 'river@example.com'}});
  fireEvent.change(screen.getByLabelText('PASSWORD'), {target: {value: 'Password1!'}});
}

describe('AuthPage', () => {
  afterEach(cleanup);

  it('disables the submit button and prevents duplicate sign-in requests', async () => {
    let finish!: () => void;
    const pending = new Promise<void>(resolve => { finish = resolve; });
    const signIn = vi.fn(() => pending);
    const client = {signIn, register: vi.fn()} as unknown as AuthPageClient;
    renderSignIn(client);

    const button = screen.getByRole('button', {name: 'Sign in'});
    fireEvent.click(button);
    fireEvent.submit(button.closest('form')!);

    expect(signIn).toHaveBeenCalledTimes(1);
    expect(button).toBeDisabled();
    finish();
    await waitFor(() => expect(signIn).toHaveBeenCalledTimes(1));
  });

  it('maps server field errors and does not show the raw exception message', async () => {
    const signIn = vi.fn().mockRejectedValue(new AuthApiError(
      422,
      'VALIDATION_ERROR',
      'Sensitive upstream detail',
      {email: 'Email is not valid.'},
      'request-form-1',
    ));
    const client = {signIn, register: vi.fn()} as unknown as AuthPageClient;
    renderSignIn(client);

    fireEvent.click(screen.getByRole('button', {name: 'Sign in'}));

    expect(await screen.findByText('Email is not valid.')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('Please check the highlighted fields.');
    expect(screen.queryByText('Sensitive upstream detail')).not.toBeInTheDocument();
  });

  it('validates recruiter registration fields before writing', () => {
    const client = {signIn: vi.fn(), register: vi.fn()} as unknown as AuthPageClient;
    render(<MemoryRouter><AuthPage mode="register" client={client}/></MemoryRouter>);

    fireEvent.click(screen.getByRole('button', {name: 'Create account'}));

    expect(screen.getByText('Full name is required.')).toBeInTheDocument();
    expect(screen.getByText('Company name is required.')).toBeInTheDocument();
    expect(screen.getByText('Enter a valid work email.')).toBeInTheDocument();
    expect(screen.getByText('Password must contain at least 8 characters.')).toBeInTheDocument();
    expect(screen.getByText('You must agree to the current terms.')).toBeInTheDocument();
    expect(client.register).not.toHaveBeenCalled();
  });
});
