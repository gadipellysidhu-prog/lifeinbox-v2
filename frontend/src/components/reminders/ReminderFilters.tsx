import React from 'react';
import { ReminderFilterCounts, ReminderFilterType } from '../../types/reminder';
import { AlertCircle, Calendar, CheckCircle2, Clock, Inbox, Search, X } from 'lucide-react';
import { twMerge } from 'tailwind-merge';

export interface ReminderFiltersProps {
  activeFilter: ReminderFilterType;
  counts: ReminderFilterCounts;
  search: string;
  onFilterChange: (filter: ReminderFilterType) => void;
  onSearchChange: (search: string) => void;
}

interface FilterItem {
  id: ReminderFilterType;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  countKey: keyof ReminderFilterCounts;
  colorClass?: string;
  badgeClass?: string;
}

const filterItems: FilterItem[] = [
  {
    id: 'all',
    label: 'All',
    icon: Inbox,
    countKey: 'all',
  },
  {
    id: 'today',
    label: 'Today',
    icon: Clock,
    countKey: 'today',
    colorClass: 'text-indigo-400',
    badgeClass: 'bg-indigo-500/20 text-indigo-300',
  },
  {
    id: 'upcoming',
    label: 'Upcoming',
    icon: Calendar,
    countKey: 'upcoming',
    colorClass: 'text-sky-400',
    badgeClass: 'bg-sky-500/20 text-sky-300',
  },
  {
    id: 'overdue',
    label: 'Overdue',
    icon: AlertCircle,
    countKey: 'overdue',
    colorClass: 'text-rose-400',
    badgeClass: 'bg-rose-500/20 text-rose-300',
  },
  {
    id: 'completed',
    label: 'Completed',
    icon: CheckCircle2,
    countKey: 'completed',
    colorClass: 'text-emerald-400',
    badgeClass: 'bg-emerald-500/20 text-emerald-300',
  },
];

export const ReminderFilters: React.FC<ReminderFiltersProps> = ({
  activeFilter,
  counts,
  search,
  onFilterChange,
  onSearchChange,
}) => {
  return (
    <div className="flex flex-col gap-4">
      {/* Search Input Bar */}
      <div className="relative w-full">
        <div className="absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none text-slate-400">
          <Search className="w-4 h-4" />
        </div>
        <input
          type="text"
          value={search}
          onChange={(e) => onSearchChange(e.target.value)}
          placeholder="Search reminders by title or notes..."
          className="w-full bg-slate-900/90 text-slate-100 placeholder-slate-500 rounded-xl pl-10 pr-10 py-2.5 text-sm border border-slate-800 focus:outline-none focus:ring-2 focus:ring-indigo-500/50 focus:border-indigo-500 transition-all shadow-inner"
        />
        {search && (
          <button
            type="button"
            onClick={() => onSearchChange('')}
            aria-label="Clear search"
            className="absolute right-3.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-200 p-0.5 rounded-full"
          >
            <X className="w-4 h-4" />
          </button>
        )}
      </div>

      {/* Filter Navigation Tabs */}
      <div className="flex items-center gap-1.5 overflow-x-auto pb-1 scrollbar-none">
        {filterItems.map((item) => {
          const Icon = item.icon;
          const isActive = activeFilter === item.id;
          const count = counts[item.countKey] || 0;

          return (
            <button
              key={item.id}
              type="button"
              onClick={() => onFilterChange(item.id)}
              className={twMerge(
                'flex items-center gap-2 px-3.5 py-2 rounded-xl text-sm font-medium whitespace-nowrap transition-all duration-150',
                isActive
                  ? 'bg-indigo-600 text-white shadow-sm shadow-indigo-600/30 font-semibold'
                  : 'bg-slate-900/60 text-slate-400 hover:text-slate-200 hover:bg-slate-800/80 border border-slate-800/60'
              )}
            >
              <Icon
                className={twMerge(
                  'w-4 h-4',
                  isActive ? 'text-white' : item.colorClass || 'text-slate-400'
                )}
              />
              <span>{item.label}</span>
              <span
                className={twMerge(
                  'text-xs px-2 py-0.5 rounded-full font-mono font-medium',
                  isActive
                    ? 'bg-white/20 text-white'
                    : item.badgeClass || 'bg-slate-800 text-slate-400'
                )}
              >
                {count}
              </span>
            </button>
          );
        })}
      </div>
    </div>
  );
};

export default ReminderFilters;
