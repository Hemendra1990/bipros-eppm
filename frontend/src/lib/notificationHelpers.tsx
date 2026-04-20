'use client';

import toast from 'react-hot-toast';
import { AxiosError } from 'axios';

export const notificationHelpers = {
  /**
   * Handle API errors and show appropriate toast
   */
  handleApiError(error: unknown, fallbackMessage = 'An error occurred') {
    if (error instanceof AxiosError) {
      const rawError = error.response?.data?.error;
      const message = (typeof rawError === 'object' && rawError !== null && 'message' in rawError)
        ? (rawError as { message: string }).message
        : (typeof rawError === 'string' ? rawError : error.message || fallbackMessage);
      toast.error(message);
    } else if (error instanceof Error) {
      toast.error(error.message);
    } else {
      toast.error(fallbackMessage);
    }
  },

  /**
   * Show success notification for data creation
   */
  creationSuccess(entityName: string) {
    toast.success(`${entityName} created successfully`);
  },

  /**
   * Show success notification for data update
   */
  updateSuccess(entityName: string) {
    toast.success(`${entityName} updated successfully`);
  },

  /**
   * Show success notification for data deletion
   */
  deletionSuccess(entityName: string) {
    toast.success(`${entityName} deleted successfully`);
  },

  /**
   * Wrap an async operation with loading toast
   */
  async withPromiseToast<T>(
    operation: Promise<T>,
    operationName: string
  ): Promise<T> {
    return toast.promise(
      operation,
      {
        loading: `${operationName}...`,
        success: `${operationName} completed`,
        error: `${operationName} failed`,
      }
    );
  },
};

/**
 * Project-specific notification shortcuts
 */
export const projectNotifications = {
  created: () => toast.success('Project created successfully'),
  updated: () => toast.success('Project updated successfully'),
  deleted: () => toast.success('Project deleted successfully'),
  createdError: () => toast.error('Failed to create project'),
  updatedError: () => toast.error('Failed to update project'),
  deletedError: () => toast.error('Failed to delete project'),
};

/**
 * Activity-specific notification shortcuts
 */
export const activityNotifications = {
  created: () => toast.success('Activity created successfully'),
  updated: () => toast.success('Activity updated successfully'),
  deleted: () => toast.success('Activity deleted successfully'),
  createdError: () => toast.error('Failed to create activity'),
  updatedError: () => toast.error('Failed to update activity'),
  deletedError: () => toast.error('Failed to delete activity'),
};

/**
 * Resource-specific notification shortcuts
 */
export const resourceNotifications = {
  created: () => toast.success('Resource created successfully'),
  updated: () => toast.success('Resource updated successfully'),
  deleted: () => toast.success('Resource deleted successfully'),
  allocated: () => toast.success('Resource allocated successfully'),
  createdError: () => toast.error('Failed to create resource'),
  updatedError: () => toast.error('Failed to update resource'),
  deletedError: () => toast.error('Failed to delete resource'),
  allocationError: () => toast.error('Failed to allocate resource'),
};

/**
 * Baseline-specific notification shortcuts
 */
export const baselineNotifications = {
  created: () => toast.success('Baseline created successfully'),
  updated: () => toast.success('Baseline updated successfully'),
  deleted: () => toast.success('Baseline deleted successfully'),
  createdError: () => toast.error('Failed to create baseline'),
  updatedError: () => toast.error('Failed to update baseline'),
  deletedError: () => toast.error('Failed to delete baseline'),
};

/**
 * Schedule-specific notification shortcuts
 */
export const scheduleNotifications = {
  calculated: () => toast.success('Schedule calculated successfully'),
  updated: () => toast.success('Schedule updated successfully'),
  publishedError: () => toast.error('Failed to publish schedule'),
  calculationError: () => toast.error('Failed to calculate schedule'),
};

/**
 * Show confirmation dialog before deletion
 */
export function confirmDeletion(entityName: string, onConfirm: () => void) {
  const undoId = toast.custom((t) => (
    <div className="flex items-center gap-2 rounded-lg bg-slate-900/50 p-4 shadow-lg">
      <p className="text-sm font-medium text-white">
        Delete {entityName}?
      </p>
      <button
        onClick={() => {
          onConfirm();
          toast.dismiss(t.id);
        }}
        className="rounded bg-red-600 px-3 py-1 text-sm font-medium text-white hover:bg-red-600"
      >
        Delete
      </button>
      <button
        onClick={() => toast.dismiss(t.id)}
        className="rounded bg-slate-700/50 px-3 py-1 text-sm font-medium text-white hover:bg-slate-700"
      >
        Cancel
      </button>
    </div>
  ));
  return undoId;
}
