import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { ReminderFilters } from './ReminderFilters';
import { ReminderFilterCounts } from '../../types/reminder';

const mockCounts: ReminderFilterCounts = {
  all: 10,
  today: 3,
  upcoming: 4,
  overdue: 2,
  completed: 1,
};

describe('ReminderFilters component', () => {
  it('renders all filter tabs and displays their counts', () => {
    const onFilterChange = vi.fn();
    const onSearchChange = vi.fn();

    render(
      <ReminderFilters
        activeFilter="all"
        counts={mockCounts}
        search=""
        onFilterChange={onFilterChange}
        onSearchChange={onSearchChange}
      />
    );

    expect(screen.getByText('All')).toBeInTheDocument();
    expect(screen.getByText('10')).toBeInTheDocument();

    expect(screen.getByText('Today')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();

    expect(screen.getByText('Upcoming')).toBeInTheDocument();
    expect(screen.getByText('4')).toBeInTheDocument();

    expect(screen.getByText('Overdue')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();

    expect(screen.getByText('Completed')).toBeInTheDocument();
    expect(screen.getByText('1')).toBeInTheDocument();
  });

  it('triggers onFilterChange when a tab is clicked', () => {
    const onFilterChange = vi.fn();
    const onSearchChange = vi.fn();

    render(
      <ReminderFilters
        activeFilter="all"
        counts={mockCounts}
        search=""
        onFilterChange={onFilterChange}
        onSearchChange={onSearchChange}
      />
    );

    fireEvent.click(screen.getByText('Today'));
    expect(onFilterChange).toHaveBeenCalledWith('today');

    fireEvent.click(screen.getByText('Overdue'));
    expect(onFilterChange).toHaveBeenCalledWith('overdue');
  });

  it('triggers onSearchChange when user types in search input', () => {
    const onFilterChange = vi.fn();
    const onSearchChange = vi.fn();

    render(
      <ReminderFilters
        activeFilter="all"
        counts={mockCounts}
        search="Insurance"
        onFilterChange={onFilterChange}
        onSearchChange={onSearchChange}
      />
    );

    const input = screen.getByPlaceholderText(/Search reminders/i);
    expect(input).toHaveValue('Insurance');

    fireEvent.change(input, { target: { value: 'Tax' } });
    expect(onSearchChange).toHaveBeenCalledWith('Tax');
  });

  it('clears search when clear button is clicked', () => {
    const onFilterChange = vi.fn();
    const onSearchChange = vi.fn();

    render(
      <ReminderFilters
        activeFilter="all"
        counts={mockCounts}
        search="Insurance"
        onFilterChange={onFilterChange}
        onSearchChange={onSearchChange}
      />
    );

    const clearBtn = screen.getByRole('button', { name: /clear search/i });
    fireEvent.click(clearBtn);
    expect(onSearchChange).toHaveBeenCalledWith('');
  });
});
