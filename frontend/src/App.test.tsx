import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import App from './App';

describe('App', () => {
  it('renders the four steps of the flow', () => {
    render(<App />);

    expect(screen.getByRole('heading', { name: /portability platform/i })).toBeTruthy();
    expect(screen.getByText(/authorization \(opt-in\)/i)).toBeTruthy();
    expect(screen.getByText(/discount agenda/i)).toBeTruthy();
    expect(screen.getByText(/portability operation/i)).toBeTruthy();
    expect(screen.getByText(/audit trail/i)).toBeTruthy();
  });
});
