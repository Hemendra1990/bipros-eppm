import toast from 'react-hot-toast';

export function useNotification() {
  const success = (message: string) => {
    toast.success(message, {
      duration: 3000,
      icon: '✓',
    });
  };

  const error = (message: string) => {
    toast.error(message, {
      duration: 4000,
      icon: '✕',
    });
  };

  const loading = (message: string) => {
    return toast.loading(message);
  };

  const dismiss = (toastId?: string) => {
    if (toastId) {
      toast.dismiss(toastId);
    } else {
      toast.dismiss();
    }
  };

  const promise = <T,>(
    promise: Promise<T>,
    messages: {
      loading: string;
      success: string;
      error: string;
    }
  ) => {
    return toast.promise(
      promise,
      messages
    );
  };

  return {
    success,
    error,
    loading,
    dismiss,
    promise,
  };
}
