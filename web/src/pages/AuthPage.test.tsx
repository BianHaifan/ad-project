import '@testing-library/jest-dom/vitest';
import {cleanup, fireEvent, render, screen, waitFor} from '@testing-library/react';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {AuthApiError, type AuthClient} from '../api/authClient';
import {AuthPage} from './AuthPage';

type AuthPageClient = Pick<AuthClient, 'signIn' | 'register'>;

function renderSignIn(client: AuthPageClient) {
  render(<MemoryRouter><AuthPage mode="signin" client={client}/></MemoryRouter>);
  fireEvent.change(screen.getByLabelText('WORK EMAIL'), {target: {value: 'river@example.com'}});
  fireEvent.change(screen.getByLabelText('PASSWORD'), {target: {value: 'Password1!'}});
}

function renderRegister(client: AuthPageClient) {
  return render(
    <MemoryRouter initialEntries={['/recruiter/create-account']}>
      <Routes>
        <Route path="/recruiter/create-account" element={<AuthPage mode="register" client={client}/>} />
        <Route path="/recruiter/dashboard" element={<div>Recruiter Dashboard</div>} />
      </Routes>
    </MemoryRouter>
  );
}

function fillRegisterForm() {
  fireEvent.change(screen.getByLabelText('FULL NAME'), {target: {value: 'River Recruiter'}});
  fireEvent.change(screen.getByLabelText('COMPANY NAME'), {target: {value: 'River Labs'}});
  fireEvent.change(screen.getByLabelText('WORK EMAIL'), {target: {value: 'river@example.com'}});
  fireEvent.change(screen.getByLabelText('PASSWORD'), {target: {value: 'Password1!'}});
  fireEvent.change(screen.getByLabelText('CONFIRM PASSWORD'), {target: {value: 'Password1!'}});
  fireEvent.click(screen.getByRole('checkbox'));
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

  it('shows confirm password only on the register form', () => {
    const client = {signIn: vi.fn(), register: vi.fn()} as unknown as AuthPageClient;
    const {unmount} = render(<MemoryRouter><AuthPage mode="register" client={client}/></MemoryRouter>);
    expect(screen.getByLabelText('CONFIRM PASSWORD')).toBeInTheDocument();
    unmount();

    render(<MemoryRouter><AuthPage mode="signin" client={client}/></MemoryRouter>);
    expect(screen.queryByLabelText('CONFIRM PASSWORD')).not.toBeInTheDocument();
  });

  it('blocks registration when passwords do not match', () => {
    const register = vi.fn();
    const client = {signIn: vi.fn(), register} as unknown as AuthPageClient;
    render(<MemoryRouter><AuthPage mode="register" client={client}/></MemoryRouter>);

    fireEvent.change(screen.getByLabelText('FULL NAME'), {target: {value: 'River Recruiter'}});
    fireEvent.change(screen.getByLabelText('COMPANY NAME'), {target: {value: 'River Labs'}});
    fireEvent.change(screen.getByLabelText('WORK EMAIL'), {target: {value: 'river@example.com'}});
    fireEvent.change(screen.getByLabelText('PASSWORD'), {target: {value: 'Password1!'}});
    fireEvent.change(screen.getByLabelText('CONFIRM PASSWORD'), {target: {value: 'Different1!'}});
    fireEvent.click(screen.getByRole('checkbox'));
    fireEvent.click(screen.getByRole('button', {name: 'Create account'}));

    expect(screen.getByText('Passwords do not match.')).toBeInTheDocument();
    expect(register).not.toHaveBeenCalled();
  });

  it('submits only one password field when passwords match', async () => {
    const register = vi.fn().mockResolvedValue(undefined);
    const client = {signIn: vi.fn(), register} as unknown as AuthPageClient;
    renderRegister(client);
    fillRegisterForm();

    fireEvent.click(screen.getByRole('button', {name: 'Create account'}));

    await waitFor(() => expect(register).toHaveBeenCalledTimes(1));
    const input = register.mock.calls[0][0] as Record<string, unknown>;
    expect(input.password).toBe('Password1!');
    expect(input).not.toHaveProperty('confirmPassword');
  });

  it('clears server field errors when the field is edited', async () => {
    const register = vi.fn().mockRejectedValue(new AuthApiError(
      422, 'VALIDATION_ERROR', 'Request validation failed', {email: 'Email is not valid.'}, 'request-2',
    ));
    const client = {signIn: vi.fn(), register} as unknown as AuthPageClient;
    render(<MemoryRouter><AuthPage mode="register" client={client}/></MemoryRouter>);
    fillRegisterForm();

    fireEvent.click(screen.getByRole('button', {name: 'Create account'}));

    expect(await screen.findByText('Email is not valid.')).toBeInTheDocument();
    fireEvent.change(screen.getByPlaceholderText('recruiter@company.com'), {target: {value: 'other@example.com'}});
    expect(screen.queryByText('Email is not valid.')).not.toBeInTheDocument();
  });

  it('recovers from a server failure after editing input', async () => {
    const register = vi.fn()
      .mockRejectedValueOnce(new AuthApiError(409, 'EMAIL_ALREADY_REGISTERED', 'Email already registered', {}, 'request-1'))
      .mockResolvedValueOnce(undefined);
    const client = {signIn: vi.fn(), register} as unknown as AuthPageClient;
    renderRegister(client);
    fillRegisterForm();

    fireEvent.click(screen.getByRole('button', {name: 'Create account'}));

    expect(await screen.findByText('An account already exists for this email.')).toBeInTheDocument();
    expect(register).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('button', {name: 'Create account'})).toBeEnabled();

    fireEvent.change(screen.getByLabelText('WORK EMAIL'), {target: {value: 'fresh@example.com'}});
    expect(screen.queryByText('An account already exists for this email.')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', {name: 'Create account'}));
    await waitFor(() => expect(register).toHaveBeenCalledTimes(2));
    expect(await screen.findByText('Recruiter Dashboard')).toBeInTheDocument();
  });
});
