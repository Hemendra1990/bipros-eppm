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
          background: '#363636',
          color: '#fff',
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
