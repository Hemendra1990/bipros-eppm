import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  guideSidebar: [
    {
      type: 'category',
      label: 'Getting Started',
      collapsed: false,
      items: [
        'getting-started/introduction',
        'getting-started/navigation',
        'getting-started/quick-start',
      ],
    },
    {
      type: 'category',
      label: 'Dashboard',
      collapsed: true,
      items: [
        'dashboards/overview',
        'dashboards/home-dashboard',
        'dashboards/executive',
        'dashboards/programme',
        'dashboards/operational',
        'dashboards/field',
      ],
    },
    {
      type: 'category',
      label: 'Projects',
      collapsed: true,
      items: [
        'projects/overview',
        'projects/creating-a-project',
        'projects/project-detail',
        {
          type: 'category',
          label: 'Schedule Management',
          items: [
            'projects/activities',
            'projects/activity-codes',
            'projects/wbs',
            'projects/schedule-health',
            'projects/schedule-compression',
          ],
        },
        {
          type: 'category',
          label: 'Cost & Finance',
          items: [
            'projects/contracts',
            'projects/ra-bills',
            'projects/evm',
          ],
        },
        {
          type: 'category',
          label: 'Field Operations',
          items: [
            'projects/labour-returns',
            'projects/equipment-logs',
            'projects/material-reconciliation',
          ],
        },
        {
          type: 'category',
          label: 'Documents & Communication',
          items: [
            'projects/documents',
            'projects/drawings',
            'projects/rfis',
          ],
        },
        {
          type: 'category',
          label: 'Analysis & Forecasting',
          items: [
            'projects/risk-analysis',
            'projects/predictions',
            'projects/gis-viewer',
            'projects/baselines',
            'projects/global-change',
          ],
        },
        'projects/integrations',
      ],
    },
    {
      type: 'category',
      label: 'Portfolios',
      collapsed: true,
      items: [
        'portfolios/overview',
        'portfolios/managing-portfolios',
      ],
    },
    {
      type: 'category',
      label: 'Enterprise Structure',
      collapsed: true,
      items: [
        'enterprise-structure/eps',
        'enterprise-structure/obs',
      ],
    },
    {
      type: 'category',
      label: 'Resources',
      collapsed: true,
      items: [
        'resources/overview',
        'resources/calendars',
      ],
    },
    {
      type: 'category',
      label: 'Reports & Analytics',
      collapsed: true,
      items: [
        'reports-analytics/reports',
        'reports-analytics/analytics',
      ],
    },
    {
      type: 'category',
      label: 'Risk Management',
      collapsed: true,
      items: [
        'risk/overview',
      ],
    },
    {
      type: 'category',
      label: 'Administration',
      collapsed: true,
      items: [
        'admin/settings',
        'admin/wbs-templates',
        'admin/integrations',
      ],
    },
  ],
  glossarySidebar: [
    'glossary',
  ],
};

export default sidebars;
