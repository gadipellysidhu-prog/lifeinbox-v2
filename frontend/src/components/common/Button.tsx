import React from 'react';
import { twMerge } from 'tailwind-merge';
import { Spinner } from './Spinner';

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost' | 'outline';
  size?: 'sm' | 'md' | 'lg';
  isLoading?: boolean;
  leftIcon?: React.ReactNode;
  rightIcon?: React.ReactNode;
}

const variantClasses = {
  primary:
    'bg-indigo-600 text-white hover:bg-indigo-500 active:bg-indigo-700 shadow-sm shadow-indigo-500/20 border border-indigo-500/30 focus-visible:ring-indigo-500',
  secondary:
    'bg-slate-800 text-slate-200 hover:bg-slate-700 active:bg-slate-800 border border-slate-700/80 focus-visible:ring-slate-400',
  danger:
    'bg-rose-600/90 text-white hover:bg-rose-500 active:bg-rose-700 shadow-sm shadow-rose-500/20 border border-rose-500/40 focus-visible:ring-rose-500',
  ghost:
    'bg-transparent text-slate-300 hover:bg-slate-800/80 hover:text-white active:bg-slate-800 focus-visible:ring-slate-400',
  outline:
    'bg-transparent text-slate-200 border border-slate-700 hover:border-slate-600 hover:bg-slate-800/50 focus-visible:ring-slate-400',
};

const sizeClasses = {
  sm: 'px-2.5 py-1.5 text-xs font-medium rounded-lg gap-1.5',
  md: 'px-3.5 py-2 text-sm font-medium rounded-lg gap-2',
  lg: 'px-5 py-2.5 text-base font-medium rounded-xl gap-2.5',
};

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  (
    {
      children,
      className,
      variant = 'primary',
      size = 'md',
      isLoading = false,
      disabled = false,
      leftIcon,
      rightIcon,
      type = 'button',
      ...props
    },
    ref
  ) => {
    return (
      <button
        ref={ref}
        type={type}
        disabled={disabled || isLoading}
        className={twMerge(
          'inline-flex items-center justify-center transition-all duration-150 select-none cursor-pointer',
          'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-offset-slate-950',
          'disabled:opacity-50 disabled:cursor-not-allowed disabled:pointer-events-none',
          variantClasses[variant],
          sizeClasses[size],
          className
        )}
        {...props}
      >
        {isLoading ? (
          <>
            <Spinner size="sm" className="text-current border-t-current" />
            <span>{children}</span>
          </>
        ) : (
          <>
            {leftIcon && <span className="inline-flex shrink-0">{leftIcon}</span>}
            {children}
            {rightIcon && <span className="inline-flex shrink-0">{rightIcon}</span>}
          </>
        )}
      </button>
    );
  }
);

Button.displayName = 'Button';

export default Button;
