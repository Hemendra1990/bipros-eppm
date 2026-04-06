'use client';

import { Toaster as HotToaster } from 'react-hot-toast';

export function AppToaster() {
  return (
    <HotToaster
      position="top-right"
      reverseOrder={false}
      gutter={8}
      toastOptions={{
        duration: 4000,
        style: {
          background: '#1e293b',
          color: '#f1f5f9',
          border: '1px solid rgba(255, 255, 255, 0.06)',
          borderRadius: '12px',
          zIndex: 99999,
        },
        success: {
          duration: 3000,
        },
        error: {
          duration: 4000,
        },
      }}
    />
  );
}
