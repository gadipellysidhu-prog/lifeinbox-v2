import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import App from './App';

describe('LifeInbox App', () => {
  it('renders landing page brand and header at root URL', () => {
    window.history.pushState({}, '', '/');
    render(<App />);
    expect(screen.getAllByText(/LifeInbox/i).length).toBeGreaterThan(0);
    expect(screen.getByText(/Monorepo Scaffold Initialized/i)).toBeInTheDocument();
  });

  it('renders landing page brand and header at configured BASE_URL', () => {
    window.history.pushState({}, '', import.meta.env.BASE_URL);
    render(<App />);
    expect(screen.getAllByText(/LifeInbox/i).length).toBeGreaterThan(0);
    expect(screen.getByText(/Monorepo Scaffold Initialized/i)).toBeInTheDocument();
  });
});
