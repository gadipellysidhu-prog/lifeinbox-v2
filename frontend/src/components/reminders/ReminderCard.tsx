import React, { useState } from 'react';
import { Reminder } from '../../types/reminder';
import { format, isPast, isToday, parseISO } from 'date-fns';
import {
  AlertCircle,
  Calendar,
  CheckCircle2,
  Circle,
  Clock,
  Edit2,
  Folder,
  Trash2,
} from 'lucide-react';
import { Button } from '../common/Button';
import { twMerge } from 'tailwind-merge';

export interface ReminderCardProps {
  reminder: Reminder;
  onEdit: (reminder: Reminder) => void;
  onDelete: (id: string) => Promise<void>;
  onToggleComplete: (id: string, currentStatus: string) => Promise<void>;
}

export const ReminderCard: React.FC<ReminderCardProps> = ({
  reminder,
  onEdit,
  onDelete,
  onToggleComplete,
}) => {
  const [isDeleting, setIsDeleting] = useState(false);
  const [isToggling, setIsToggling] = useState(false);
  const [showConfirmDelete, setShowConfirmDelete] = useState(false);

  const dueDate = parseISO(reminder.dueAt);
  const isOverdue = reminder.status === 'PENDING' && isPast(dueDate) && !isToday(dueDate);
  const isDueToday = reminder.status === 'PENDING' && isToday(dueDate);
  const isCompleted = reminder.status === 'COMPLETED';

  const handleToggle = async () => {
    setIsToggling(true);
    try {
      await onToggleComplete(reminder.id, reminder.status);
    } finally {
      setIsToggling(false);
    }
  };

  const handleDelete = async () => {
    setIsDeleting(true);
    try {
      await onDelete(reminder.id);
    } finally {
      setIsDeleting(false);
      setShowConfirmDelete(false);
    }
  };

  const priorityStyles = {
    LOW: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
    MEDIUM: 'bg-amber-500/10 text-amber-400 border-amber-500/20',
    HIGH: 'bg-rose-500/10 text-rose-400 border-rose-500/20',
  };

  return (
    <div
      className={twMerge(
        'group relative bg-slate-900/90 rounded-xl p-4 sm:p-5 border transition-all duration-200 shadow-sm',
        isCompleted
          ? 'border-slate-800/80 opacity-75 hover:opacity-100 bg-slate-900/40'
          : isOverdue
          ? 'border-rose-500/40 bg-rose-950/10 hover:border-rose-500/60 shadow-rose-950/20'
          : 'border-slate-800 hover:border-slate-700 hover:shadow-slate-900/50'
      )}
    >
      <div className="flex items-start gap-3 sm:gap-4">
        {/* Toggle Checkbox Button */}
        <button
          type="button"
          onClick={handleToggle}
          disabled={isToggling}
          aria-label={isCompleted ? 'Mark as incomplete' : 'Mark as completed'}
          className={twMerge(
            'mt-0.5 shrink-0 rounded-full transition-colors duration-150 p-1 focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500',
            isCompleted
              ? 'text-emerald-400 hover:text-emerald-300'
              : 'text-slate-500 hover:text-indigo-400'
          )}
        >
          {isCompleted ? (
            <CheckCircle2 className="w-5 h-5 fill-emerald-500/20" />
          ) : (
            <Circle className="w-5 h-5" />
          )}
        </button>

        {/* Reminder Content */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap mb-1">
            <h3
              className={twMerge(
                'text-base font-semibold leading-tight text-slate-100 break-words',
                isCompleted && 'line-through text-slate-400'
              )}
            >
              {reminder.title}
            </h3>
          </div>

          {/* Notes */}
          {reminder.notes && (
            <p
              className={twMerge(
                'text-sm text-slate-400 mt-1 line-clamp-3 whitespace-pre-wrap',
                isCompleted && 'text-slate-500 line-through'
              )}
            >
              {reminder.notes}
            </p>
          )}

          {/* Badges / Metadata */}
          <div className="flex flex-wrap items-center gap-2 mt-3 text-xs">
            {/* Priority Badge */}
            <span
              className={twMerge(
                'px-2 py-0.5 rounded-md font-medium border uppercase tracking-wider text-[10px]',
                priorityStyles[reminder.priority]
              )}
            >
              {reminder.priority}
            </span>

            {/* Category Badge */}
            {reminder.category && (
              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md font-medium bg-slate-800 text-slate-300 border border-slate-700/60">
                <Folder className="w-3 h-3 text-slate-400" />
                {reminder.category}
              </span>
            )}

            {/* Due Date Badge */}
            <span
              className={twMerge(
                'inline-flex items-center gap-1 px-2 py-0.5 rounded-md font-medium border',
                isCompleted
                  ? 'bg-slate-800 text-slate-400 border-slate-700/50'
                  : isOverdue
                  ? 'bg-rose-500/10 text-rose-300 border-rose-500/30'
                  : isDueToday
                  ? 'bg-indigo-500/10 text-indigo-300 border-indigo-500/30'
                  : 'bg-slate-800/80 text-slate-300 border-slate-700/60'
              )}
            >
              {isOverdue ? (
                <AlertCircle className="w-3 h-3 text-rose-400" />
              ) : isDueToday ? (
                <Clock className="w-3 h-3 text-indigo-400" />
              ) : (
                <Calendar className="w-3 h-3 text-slate-400" />
              )}
              <span>{format(dueDate, 'MMM d, yyyy · h:mm a')}</span>
              {isOverdue && <span className="font-semibold text-rose-400 ml-1">(Overdue)</span>}
              {isDueToday && <span className="font-semibold text-indigo-400 ml-1">(Today)</span>}
            </span>

            {/* Completed timestamp if completed */}
            {isCompleted && reminder.completedAt && (
              <span className="text-slate-500 text-[11px] ml-auto">
                Completed {format(parseISO(reminder.completedAt), 'MMM d, h:mm a')}
              </span>
            )}
          </div>
        </div>

        {/* Action Buttons */}
        <div className="flex items-center gap-1 shrink-0 ml-2">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => onEdit(reminder)}
            aria-label="Edit reminder"
            className="p-1.5 text-slate-400 hover:text-slate-200"
          >
            <Edit2 className="w-4 h-4" />
          </Button>

          {showConfirmDelete ? (
            <div className="flex items-center gap-1 bg-slate-950 p-1 rounded-lg border border-rose-500/50">
              <Button
                variant="danger"
                size="sm"
                isLoading={isDeleting}
                onClick={handleDelete}
                className="px-2 py-0.5 text-xs"
              >
                Delete
              </Button>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setShowConfirmDelete(false)}
                className="px-1.5 py-0.5 text-xs text-slate-400"
              >
                Cancel
              </Button>
            </div>
          ) : (
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setShowConfirmDelete(true)}
              aria-label="Delete reminder"
              className="p-1.5 text-slate-400 hover:text-rose-400"
            >
              <Trash2 className="w-4 h-4" />
            </Button>
          )}
        </div>
      </div>
    </div>
  );
};

export default ReminderCard;
