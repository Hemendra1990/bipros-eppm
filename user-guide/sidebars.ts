import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  guideSidebar: [
    {
      type: 'category',
      label: '🚀 Getting Started',
      collapsed: false,
      items: [
        'getting-started/introduction',
        'getting-started/navigation',
        'getting-started/quick-start',
        'getting-started/user-roles-permissions',
        'getting-started/ui-guide',
      ],
    },
    {
      type: 'category',
      label: '📋 Task Guides',
      collapsed: true,
      items: [
        'task-guides/index',
        'task-guides/creating-first-project',
        'task-guides/setting-up-wbs',
        'task-guides/scheduling-activities',
        'task-guides/tracking-daily-progress',
        'task-guides/managing-ra-bills',
        'task-guides/resource-planning-deployment',
        'task-guides/conducting-risk-analysis',
        'task-guides/running-schedule-compression',
        'task-guides/managing-permits',
        'task-guides/closing-project',
      ],
    },
    {
      type: 'category',
      label: '📚 Module Reference',
      collapsed: true,
      items: [
        {
          type: 'category',
          label: 'Enterprise Structure',
          items: [
            'enterprise-structure/eps',
            'enterprise-structure/obs',
          ],
        },
        {
          type: 'category',
          label: 'Projects & WBS',
          items: [
            'projects/overview',
            'projects/creating-a-project',
            'projects/project-detail',
            'projects/wbs',
            'module-reference/baselines/index',
          ],
        },
        {
          type: 'category',
          label: 'Activities & Scheduling',
          items: [
            'projects/activities',
            'projects/activity-codes',
            'module-reference/activities-scheduling/index',
            'projects/schedule-health',
            'projects/schedule-compression',
          ],
        },
        {
          type: 'category',
          label: 'Cost & Finance',
          items: [
            'module-reference/cost-management/index',
            'projects/contracts',
            'projects/ra-bills',
            'module-reference/contracts-ra-bills/index',
          ],
        },
        {
          type: 'category',
          label: 'Earned Value Management',
          items: [
            'projects/evm',
            'module-reference/evm/index',
            'module-reference/evm/formulas',
            'module-reference/evm/techniques',
          ],
        },
        {
          type: 'category',
          label: 'Resource Management',
          items: [
            'resources/overview',
            'resources/calendars',
            'module-reference/resource-management/index',
            'projects/labour-returns',
            'projects/equipment-logs',
            'projects/material-reconciliation',
          ],
        },
        {
          type: 'category',
          label: 'Risk Management',
          items: [
            'risk/overview',
            'projects/risk-analysis',
            'module-reference/ai-predictions/index',
            'projects/predictions',
          ],
        },
        {
          type: 'category',
          label: 'Documents & Communication',
          items: [
            'projects/documents',
            'projects/drawings',
            'projects/rfis',
            'module-reference/documents-drawings/index',
          ],
        },
        {
          type: 'category',
          label: 'GIS & Permits',
          items: [
            'projects/gis-viewer',
            'module-reference/gis-satellite/index',
            'module-reference/permits/index',
          ],
        },
        {
          type: 'category',
          label: 'Portfolios & Dashboards',
          items: [
            'portfolios/overview',
            'portfolios/managing-portfolios',
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
          label: 'Reports & Analytics',
          items: [
            'reports-analytics/reports',
            'reports-analytics/analytics',
          ],
        },
        {
          type: 'category',
          label: 'Integrations',
          items: [
            'projects/integrations',
            'module-reference/integrations/index',
            'admin/integrations',
          ],
        },
        {
          type: 'category',
          label: 'Security & Administration',
          items: [
            'module-reference/security-access-control/index',
            'admin/settings',
            'admin/wbs-templates',
          ],
        },
      ],
    },
    {
      type: 'category',
      label: '📎 Appendices',
      collapsed: true,
      items: [
        'appendices/formula-reference',
        'appendices/actor-use-case-matrix',
        'appendices/permission-matrix',
      ],
    },
  ],
  glossarySidebar: [
    'glossary',
  ],
};

export default sidebars;
