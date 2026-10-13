import React from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { CreateReminderRequest, Reminder, ReminderPriority } from '../../types/reminder';
import { Button } from '../common/Button';
import { Input } from '../common/Input';
import { format, parseISO } from 'date-fns';
import { Calendar, FileText, Folder, Tag, X } from 'lucide-react';

const reminderSchema = z.object({
  title: z
    .string()
    .min(1, 'Title is required')
    .max(200, 'Title cannot exceed 200 characters'),
  notes: z
    .string()
    .max(5000, 'Notes cannot exceed 5000 characters')
    .optional()
    .or(z.literal('')),
  dueAt: z.string().min(1, 'Due date and time is required'),
  priority: z.enum(['LOW', 'MEDIUM', 'HIGH'] as const, {
    errorMap: () => ({ message: 'Priority is required' }),
  }),
  category: z
    .string()
    .max(100, 'Category cannot exceed 100 characters')
    .optional()
    .or(z.literal('')),
});

type ReminderFormData = z.infer<typeof reminderSchema>;

export interface ReminderFormProps {
  initialData?: Reminder | null;
  onSubmit: (data: CreateReminderRequest) => Promise<void>;
  onCancel: () => void;
  isLoading?: boolean;
}

function getDefaultDateTimeLocal(): string {
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  tomorrow.setHours(9, 0, 0, 0);
  return format(tomorrow, "yyyy-MM-dd'T'HH:mm");
}

function formatIsoToDateTimeLocal(isoString?: string | null): string {
  if (!isoString) return getDefaultDateTimeLocal();
  try {
    const date = parseISO(isoString);
    return format(date, "yyyy-MM-dd'T'HH:mm");
  } catch {
    return getDefaultDateTimeLocal();
  }
}

export const ReminderForm: React.FC<ReminderFormProps> = ({
  initialData,
  onSubmit,
  onCancel,
  isLoading = false,
}) => {
  const isEditing = !!initialData;

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ReminderFormData>({
    resolver: zodResolver(reminderSchema),
    defaultValues: {
      title: initialData?.title || '',
      notes: initialData?.notes || '',
      dueAt: formatIsoToDateTimeLocal(initialData?.dueAt),
      priority: initialData?.priority || 'MEDIUM',
      category: initialData?.category || '',
    },
  });

  const handleFormSubmit = async (values: ReminderFormData) => {
    // Convert local datetime to UTC ISO-8601 string
    const isoDueAt = new Date(values.dueAt).toISOString();

    const payload: CreateReminderRequest = {
      title: values.title.trim(),
      notes: values.notes ? values.notes.trim() : null,
      dueAt: isoDueAt,
      priority: values.priority as ReminderPriority,
      category: values.category ? values.category.trim() : null,
    };

    await onSubmit(payload);
  };

  return (
    <form
      onSubmit={handleSubmit(handleFormSubmit)}
      className="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl flex flex-col gap-5"
    >
      <div className="flex items-center justify-between pb-3 border-b border-slate-800">
        <div>
          <h2 className="text-lg font-bold text-slate-100">
            {isEditing ? 'Edit Reminder' : 'Create New Reminder'}
          </h2>
          <p className="text-xs text-slate-400 mt-0.5">
            {isEditing
              ? 'Update your reminder details and timeline'
              : 'Add a life-admin reminder to stay on top of your tasks'}
          </p>
        </div>
        <button
          type="button"
          onClick={onCancel}
          className="text-slate-400 hover:text-slate-200 p-1 rounded-lg transition-colors"
          aria-label="Close form"
        >
          <X className="w-5 h-5" />
        </button>
      </div>

      {/* Title */}
      <Input
        label="Title"
        required
        placeholder="e.g. Renew car insurance policy"
        leftIcon={<Tag className="w-4 h-4" />}
        error={errors.title?.message}
        {...register('title')}
      />

      {/* Notes */}
      <div className="flex flex-col gap-1.5">
        <label
          htmlFor="notes"
          className="text-xs font-semibold text-slate-300 tracking-wide flex items-center gap-1.5"
        >
          <FileText className="w-3.5 h-3.5 text-slate-400" />
          <span>Notes (Optional)</span>
        </label>
        <textarea
          id="notes"
          rows={3}
          placeholder="Add additional details, policy numbers, or checklists..."
          className="w-full bg-slate-950 text-slate-100 placeholder-slate-500 rounded-lg px-3.5 py-2 text-sm border border-slate-700/80 transition-colors focus:outline-none focus:ring-2 focus:ring-indigo-500/50 focus:border-indigo-500 resize-y"
          {...register('notes')}
        />
        {errors.notes && (
          <p className="text-xs text-rose-400 font-medium">{errors.notes.message}</p>
        )}
      </div>

      {/* Grid row: Due Date & Priority & Category */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        {/* Due Date & Time */}
        <Input
          label="Due Date & Time"
          required
          type="datetime-local"
          leftIcon={<Calendar className="w-4 h-4" />}
          helperText="An email notification will be sent at this scheduled time."
          error={errors.dueAt?.message}
          {...register('dueAt')}
        />

        {/* Priority */}
        <div className="flex flex-col gap-1.5">
          <label
            htmlFor="priority"
            className="text-xs font-semibold text-slate-300 tracking-wide"
          >
            Priority <span className="text-rose-400">*</span>
          </label>
          <select
            id="priority"
            className="w-full bg-slate-950 text-slate-100 rounded-lg px-3.5 py-2 text-sm border border-slate-700/80 transition-colors focus:outline-none focus:ring-2 focus:ring-indigo-500/50 focus:border-indigo-500"
            {...register('priority')}
          >
            <option value="LOW">Low Priority</option>
            <option value="MEDIUM">Medium Priority</option>
            <option value="HIGH">High Priority (Urgent)</option>
          </select>
          {errors.priority && (
            <p className="text-xs text-rose-400 font-medium">{errors.priority.message}</p>
          )}
        </div>
      </div>

      {/* Category */}
      <Input
        label="Category (Optional)"
        placeholder="e.g. Finance, Health, Home, Work"
        leftIcon={<Folder className="w-4 h-4" />}
        error={errors.category?.message}
        {...register('category')}
      />

      {/* Buttons */}
      <div className="flex items-center justify-end gap-3 pt-3 border-t border-slate-800 mt-2">
        <Button variant="ghost" onClick={onCancel} disabled={isLoading}>
          Cancel
        </Button>
        <Button type="submit" variant="primary" isLoading={isLoading}>
          {isEditing ? 'Save Changes' : 'Create Reminder'}
        </Button>
      </div>
    </form>
  );
};

export default ReminderForm;
