'use client';

import React, { useState } from 'react';

export interface DialogProps {
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
  children: React.ReactNode;
}

export function Dialog({ open = false, onOpenChange, children }: DialogProps) {
  const [isOpen, setIsOpen] = useState(open);

  const handleOpenChange = (newOpen: boolean) => {
    setIsOpen(newOpen);
    onOpenChange?.(newOpen);
  };

  return (
    <DialogContext.Provider value={{ isOpen, onOpenChange: handleOpenChange }}>
      {children}
    </DialogContext.Provider>
  );
}

interface DialogContextType {
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
}

const DialogContext = React.createContext<DialogContextType | undefined>(undefined);

function useDialog() {
  const context = React.useContext(DialogContext);
  if (!context) {
    throw new Error('Dialog components must be used within a Dialog');
  }
  return context;
}

export interface DialogContentProps extends React.HTMLAttributes<HTMLDivElement> {}

export function DialogContent({ className = '', children, ...props }: DialogContentProps) {
  const { isOpen, onOpenChange } = useDialog();

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center">
      <div
        className={`bg-slate-900/50 rounded-lg shadow-lg max-w-md w-full mx-4 ${className}`}
        {...props}
      >
        {children}
        <button
          onClick={() => onOpenChange(false)}
          className="absolute top-2 right-2 text-slate-500 hover:text-slate-300"
        >
          ×
        </button>
      </div>
    </div>
  );
}

export interface DialogHeaderProps extends React.HTMLAttributes<HTMLDivElement> {}

export function DialogHeader({ className = '', ...props }: DialogHeaderProps) {
  return <div className={`px-6 py-4 border-b border-slate-800 ${className}`} {...props} />;
}

export interface DialogTitleProps extends React.HTMLAttributes<HTMLHeadingElement> {}

export function DialogTitle({ className = '', ...props }: DialogTitleProps) {
  return <h2 className={`text-lg font-semibold ${className}`} {...props} />;
}
