import { test, expect } from '@playwright/test';
import { login } from '../fixtures/auth.fixture';

const issues: string[] = [];
function logIssue(category: string, msg: string) {
  const entry = `[${category}] ${msg}`;
  issues.push(entry);
  console.log(`  ✘ ${entry}`);
}

function logOk(msg: string) {
  console.log(`  ✓ ${msg}`);
}

test.describe('Full App Exploration', () => {

  test('browse every page and log issues', async ({ page }) => {

    // ─── LOGIN ───
    console.log('\n=== AUTH LOGIN ===');
    await page.goto('http://localhost:3000/auth/login');
    await page.waitForLoadState('networkidle');

    // Check login form fields
    const usernameInput = page.locator('#username');
    const passwordInput = page.locator('#password');
    await expect(usernameInput).toBeVisible();
    await expect(passwordInput).toBeVisible();
    logOk('Login form fields visible');

    // Login
    await usernameInput.fill('admin');
    await passwordInput.fill('admin123');
    await page.getByRole('button', { name: /sign in/i }).click();
    await page.waitForURL('/', { timeout: 10000 });
    logOk('Login succeeds, redirected to /');

    // ─── DASHBOARD ───
    console.log('\n=== DASHBOARD (/) ===');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);

    const dashH1 = page.getByRole('heading', { name: 'Dashboard', level: 1 });
    if (await dashH1.isVisible()) logOk('Dashboard heading visible');
    else logIssue('Dashboard', 'H1 "Dashboard" not visible');

    // Check metric cards
    const planned = await page.getByText('Planned').isVisible().catch(() => false);
    const active = await page.getByText('Active').isVisible().catch(() => false);
    const completed = await page.getByText('Completed').isVisible().catch(() => false);
    if (planned && active && completed) logOk('Status metric cards visible');
    else logIssue('Dashboard', `Missing metric cards: Planned=${planned} Active=${active} Completed=${completed}`);

    // Check recent projects table
    const recentH2 = await page.getByRole('heading', { name: 'Recent Projects' }).isVisible().catch(() => false);
    if (recentH2) logOk('Recent Projects section visible');
    else logIssue('Dashboard', 'Recent Projects section missing');

    // Quick Actions links
    const qaProjects = await page.getByRole('link', { name: /^Projects$/ }).isVisible().catch(() => false);
    const qaResources = await page.getByRole('link', { name: /^Resources$/ }).isVisible().catch(() => false);
    if (qaProjects && qaResources) logOk('Quick Actions links present');
    else logIssue('Dashboard', `Quick Actions incomplete: Projects=${qaProjects} Resources=${qaResources}`);

    // Check for red error text on dashboard
    const dashErrors = await page.locator('.text-red-500, .text-red-700').filter({ hasNotText: /^0$/ }).allTextContents();
    if (dashErrors.length) logIssue('Dashboard', `Error text found: ${dashErrors.join('; ')}`);
    else logOk('No error text on dashboard');

    // ─── EPS ───
    console.log('\n=== EPS PAGE (/eps) ===');
    await page.goto('http://localhost:3000/eps');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1500);

    await expect(page.getByRole('heading', { name: 'Enterprise Project Structure' })).toBeVisible();
    logOk('EPS title visible');

    // Check tree has seeded nodes
    const entNode = await page.getByText('ENT').isVisible().catch(() => false);
    const infraNode = await page.getByText('INFRA').isVisible().catch(() => false);
    const commNode = await page.getByText('COMM').isVisible().catch(() => false);
    if (entNode && infraNode && commNode) logOk('Seeded EPS nodes visible (ENT, INFRA, COMM)');
    else logIssue('EPS', `Missing seeded nodes: ENT=${entNode} INFRA=${infraNode} COMM=${commNode}`);

    const epsErrors = await page.locator('.text-red-500, .text-red-700').allTextContents();
    if (epsErrors.length) logIssue('EPS', `Error text: ${epsErrors.join('; ')}`);
    else logOk('No errors on EPS page');

    // Check Add Node button
    const addBtn = await page.getByRole('button', { name: /add node/i }).isVisible().catch(() => false);
    if (addBtn) logOk('Add Node button visible');
    else logIssue('EPS', 'Add Node button not found');

    // Open form and check fields
    if (addBtn) {
      await page.getByRole('button', { name: /add node/i }).click();
      await page.waitForTimeout(500);
      const codeInput = await page.getByPlaceholder('Code').isVisible().catch(() => false);
      const nameInput = await page.getByPlaceholder('Name').isVisible().catch(() => false);
      const createBtn = await page.getByRole('button', { name: /^Create$/i }).isVisible().catch(() => false);
      if (codeInput && nameInput && createBtn) logOk('EPS create form has Code, Name, Create button');
      else logIssue('EPS', `Form incomplete: Code=${codeInput} Name=${nameInput} Create=${createBtn}`);
      
      // Cancel
      await page.keyboard.press('Escape');
      await page.waitForTimeout(300);
    }

    // ─── PROJECTS LIST ───
    console.log('\n=== PROJECTS PAGE (/projects) ===');
    await page.goto('http://localhost:3000/projects');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1500);

    await expect(page.getByRole('heading', { name: 'Projects' })).toBeVisible();
    logOk('Projects title visible');

    const projRows = await page.locator('table tbody tr').count();
    if (projRows > 0) logOk(`Projects table has ${projRows} rows`);
    else logIssue('Projects', 'Table has 0 rows — no projects displayed');

    // Check table headers
    const headers = await page.locator('table thead th').allTextContents();
    const expectedHeaders = ['Code', 'Name', 'Status', 'Start Date', 'Finish Date'];
    const missingHeaders = expectedHeaders.filter(h => !headers.some(th => th.includes(h)));
    if (missingHeaders.length === 0) logOk(`Table headers complete: ${headers.join(', ')}`);
    else logIssue('Projects', `Missing table headers: ${missingHeaders.join(', ')}`);

    const projErrors = await page.locator('.text-red-500, .text-red-700').allTextContents();
    if (projErrors.length) logIssue('Projects', `Error text: ${projErrors.join('; ')}`);

    // ─── NEW PROJECT FORM ───
    console.log('\n=== NEW PROJECT FORM (/projects/new) ===');
    await page.goto('http://localhost:3000/projects/new');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1000);

    await expect(page.getByRole('heading', { name: 'New Project' })).toBeVisible();
    logOk('New Project form title visible');

    const formFields = ['code', 'name', 'description', 'epsNodeId', 'plannedStartDate', 'plannedFinishDate', 'priority'];
    const missingFields: string[] = [];
    for (const f of formFields) {
      const visible = await page.locator(`[name="${f}"]`).isVisible().catch(() => false);
      if (!visible) missingFields.push(f);
    }
    if (missingFields.length === 0) logOk(`All ${formFields.length} form fields visible`);
    else logIssue('New Project', `Missing form fields: ${missingFields.join(', ')}`);

    // Check EPS dropdown has options
    const epsOpts = await page.locator('[name="epsNodeId"] option').count();
    if (epsOpts > 1) logOk(`EPS dropdown has ${epsOpts} options`);
    else logIssue('New Project', `EPS dropdown has only ${epsOpts} options — no EPS nodes available to select`);

    const createProjBtn = await page.getByRole('button', { name: /create project/i }).isVisible().catch(() => false);
    if (createProjBtn) logOk('Create Project button visible');
    else logIssue('New Project', 'Create Project button not found');

    // ─── PROJECT DETAIL ───
    console.log('\n=== PROJECT DETAIL ===');
    await page.goto('http://localhost:3000/projects');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1000);

    const firstProjLink = page.locator('table tbody tr a').first();
    if (await firstProjLink.isVisible().catch(() => false)) {
      const name = await firstProjLink.textContent();
      await firstProjLink.click();
      await page.waitForURL(/\/projects\//, { timeout: 10000 });
      await page.waitForTimeout(1500);
      logOk(`Opened project: ${name?.trim()}`);

      // Check project name heading
      const projHeading = await page.getByRole('heading', { level: 1 }).first().textContent();
      if (projHeading && projHeading.length > 0) logOk(`Project heading: "${projHeading}"`);
      else logIssue('Project Detail', 'No project name heading');

      // Check tabs
      const tabs = ['Overview', 'WBS', 'Activities', 'Gantt', 'Resources', 'Costs', 'EVM'];
      const missingTabs: string[] = [];
      for (const tab of tabs) {
        const visible = await page.getByText(tab).first().isVisible().catch(() => false);
        if (!visible) missingTabs.push(tab);
      }
      if (missingTabs.length === 0) logOk(`All ${tabs.length} tabs visible`);
      else logIssue('Project Detail', `Missing tabs: ${missingTabs.join(', ')}`);

      // Check Overview tab content
      const overviewContent = await page.locator('main').textContent();
      if (overviewContent && overviewContent.length > 50) logOk('Overview tab has content');
      else logIssue('Project Detail', 'Overview tab appears empty');

      // ─── WBS TAB ───
      console.log('\n  --- WBS TAB ---');
      await page.getByText('WBS').first().click();
      await page.waitForTimeout(1500);
      const wbsContent = await page.locator('main').textContent();
      const wbsErrors = await page.locator('.text-red-500, .text-red-700').allTextContents();
      if (wbsErrors.length) logIssue('WBS Tab', `Error text: ${wbsErrors.join('; ')}`);
      else if (wbsContent && wbsContent.includes('WBS')) logOk('WBS tab has content');
      else logIssue('WBS Tab', 'WBS tab appears empty or no WBS nodes');

      // ─── ACTIVITIES TAB ───
      console.log('\n  --- ACTIVITIES TAB ---');
      await page.getByText('Activities').first().click();
      await page.waitForTimeout(1500);
      const actTables = await page.locator('table').count();
      const newActBtn = await page.getByRole('button', { name: /new activity/i }).isVisible().catch(() => false);
      if (actTables > 0 || newActBtn) logOk(`Activities tab: tables=${actTables}, New Activity button=${newActBtn}`);
      else logIssue('Activities Tab', 'No table or New Activity button found');
      
      const actErrors = await page.locator('.text-red-500, .text-red-700').allTextContents();
      if (actErrors.length) logIssue('Activities Tab', `Error text: ${actErrors.join('; ')}`);

      // ─── GANTT TAB ───
      console.log('\n  --- GANTT TAB ---');
      await page.getByText('Gantt').first().click();
      await page.waitForTimeout(2000);
      const svgCount = await page.locator('svg').count();
      const canvasCount = await page.locator('canvas').count();
      if (svgCount > 0 || canvasCount > 0) logOk(`Gantt rendered: ${svgCount} SVG, ${canvasCount} canvas elements`);
      else logIssue('Gantt Tab', 'No SVG or canvas elements — Gantt chart not rendered');

      const ganttErrors = await page.locator('.text-red-500, .text-red-700').allTextContents();
      if (ganttErrors.length) logIssue('Gantt Tab', `Error text: ${ganttErrors.join('; ')}`);

      // ─── RESOURCES TAB ───
      console.log('\n  --- RESOURCES TAB ---');
      await page.getByText('Resources').first().click();
      await page.waitForTimeout(1500);
      const resTabContent = await page.locator('main').textContent();
      if (resTabContent && resTabContent.length > 20) logOk('Resources tab has content');
      else logIssue('Resources Tab', 'Resources tab appears empty');

      // ─── COSTS TAB ───
      console.log('\n  --- COSTS TAB ---');
      await page.locator('nav >> text=Costs').click({ timeout: 5000 }).catch(() => logIssue('Costs Tab', 'Tab not clickable'));
      await page.waitForTimeout(1500);
      const costErrors = await page.locator('.text-red-500, .text-red-700').allTextContents();
      if (costErrors.length) logIssue('Costs Tab', `Error text: ${costErrors.join('; ')}`);
      else logOk('Costs tab loaded without visible errors');

      // ─── EVM TAB ───
      console.log('\n  --- EVM TAB ---');
      await page.locator('nav >> text=EVM').click({ timeout: 5000 }).catch(() => logIssue('EVM Tab', 'Tab not clickable'));
      await page.waitForTimeout(2000);
      const evmErrors = await page.locator('.text-red-500, .text-red-700').allTextContents();
      if (evmErrors.length) logIssue('EVM Tab', `Error text: ${evmErrors.join('; ')}`);
      else logOk('EVM tab loaded without visible errors');

      const evmCharts = await page.locator('svg').count();
      if (evmCharts > 0) logOk(`EVM has ${evmCharts} SVG chart elements`);
      else logIssue('EVM Tab', 'No charts rendered on EVM tab');
    }

    // ─── RESOURCES PAGE ───
    console.log('\n=== RESOURCES PAGE (/resources) ===');
    await page.goto('http://localhost:3000/resources');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1500);

    await expect(page.getByRole('heading', { name: 'Resources' })).toBeVisible();
    logOk('Resources title visible');

    const resRows = await page.locator('table tbody tr').count();
    if (resRows > 0) logOk(`Resources table has ${resRows} rows`);
    else logIssue('Resources', 'Table has 0 rows');

    const resHeaders = await page.locator('table thead th').allTextContents();
    logOk(`Resource headers: ${resHeaders.join(', ')}`);

    const newResLink = await page.getByRole('link', { name: /new resource/i }).isVisible().catch(() => false);
    if (newResLink) logOk('New Resource link visible');
    else logIssue('Resources', 'New Resource link not found');

    // Check resource types displayed
    const labor = await page.getByText('LABOR').isVisible().catch(() => false);
    const nonLabor = await page.getByText('NONLABOR').isVisible().catch(() => false);
    const material = await page.getByText('MATERIAL').isVisible().catch(() => false);
    logOk(`Resource types: LABOR=${labor} NONLABOR=${nonLabor} MATERIAL=${material}`);

    // ─── NEW RESOURCE FORM ───
    console.log('\n=== NEW RESOURCE FORM (/resources/new) ===');
    await page.goto('http://localhost:3000/resources/new');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1000);

    const newResTitle = await page.getByRole('heading', { name: /new resource/i }).isVisible().catch(() => false);
    if (newResTitle) logOk('New Resource title visible');
    else logIssue('New Resource', 'Page title not found');

    const resFormFields = ['code', 'name', 'resourceType', 'maxUnitsPerDay'];
    const missingResFields: string[] = [];
    for (const f of resFormFields) {
      const visible = await page.locator(`[name="${f}"]`).isVisible().catch(() => false);
      if (!visible) missingResFields.push(f);
    }
    if (missingResFields.length === 0) logOk('All resource form fields visible');
    else logIssue('New Resource', `Missing fields: ${missingResFields.join(', ')}`);

    // ─── CALENDARS ───
    console.log('\n=== CALENDARS PAGE (/admin/calendars) ===');
    await page.goto('http://localhost:3000/admin/calendars');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1500);

    const calTitle = await page.getByRole('heading', { name: /calendar/i }).isVisible().catch(() => false);
    if (calTitle) logOk('Calendars title visible');
    else logIssue('Calendars', 'Page title not found');

    const calRows = await page.locator('table tbody tr').count();
    logOk(`Calendar rows: ${calRows}`);

    const calErrors = await page.locator('.text-red-500, .text-red-700').allTextContents();
    if (calErrors.length) logIssue('Calendars', `Error text: ${calErrors.join('; ')}`);
    else logOk('No errors on Calendars page');

    const newCalLink = await page.getByRole('link', { name: /new calendar/i }).isVisible().catch(() => false);
    const newCalBtn = await page.getByRole('button', { name: /new calendar/i }).isVisible().catch(() => false);
    if (newCalLink || newCalBtn) logOk('New Calendar link/button visible');
    else logIssue('Calendars', 'No New Calendar action found');

    // ─── PORTFOLIOS ───
    console.log('\n=== PORTFOLIOS PAGE (/portfolios) ===');
    await page.goto('http://localhost:3000/portfolios');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1500);

    const portTitle = await page.getByRole('heading', { name: /portfolio/i }).isVisible().catch(() => false);
    if (portTitle) logOk('Portfolios title visible');
    else logIssue('Portfolios', 'Page title not found');

    const portErrors = await page.locator('.text-red-500, .text-red-700').allTextContents();
    if (portErrors.length) logIssue('Portfolios', `Error text: ${portErrors.join('; ')}`);
    else logOk('No errors on Portfolios page');

    // ─── RISK ───
    console.log('\n=== RISK PAGE (/risk) ===');
    await page.goto('http://localhost:3000/risk');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1500);

    const riskTitle = await page.getByRole('heading', { name: /risk/i }).isVisible().catch(() => false);
    if (riskTitle) logOk('Risk title visible');
    else logIssue('Risk', 'Page title not found');

    const riskErrors = await page.locator('.text-red-500, .text-red-700').allTextContents();
    if (riskErrors.length) logIssue('Risk', `Error text: ${riskErrors.join('; ')}`);
    else logOk('No errors on Risk page');

    // ─── REPORTS ───
    console.log('\n=== REPORTS PAGE (/reports) ===');
    await page.goto('http://localhost:3000/reports');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1500);

    const repTitle = await page.getByRole('heading', { name: /report/i }).isVisible().catch(() => false);
    if (repTitle) logOk('Reports title visible');
    else logIssue('Reports', 'Page title not found');

    const repErrors = await page.locator('.text-red-500, .text-red-700').allTextContents();
    if (repErrors.length) logIssue('Reports', `Error text: ${repErrors.join('; ')}`);
    else logOk('No errors on Reports page');

    // ─── OBS ───
    console.log('\n=== OBS PAGE (/obs) ===');
    await page.goto('http://localhost:3000/obs');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1500);

    const obsTitle = await page.getByRole('heading', { name: /obs/i }).isVisible().catch(() => false);
    if (obsTitle) logOk('OBS title visible');
    else logIssue('OBS', 'Page title not found');

    const obsErrors = await page.locator('.text-red-500, .text-red-700').allTextContents();
    if (obsErrors.length) logIssue('OBS', `Error text: ${obsErrors.join('; ')}`);
    else logOk('No errors on OBS page');

    // ─── ADMIN SETTINGS ───
    console.log('\n=== ADMIN SETTINGS (/admin/settings) ===');
    await page.goto('http://localhost:3000/admin/settings');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1500);

    const adminTitle = await page.getByRole('heading', { name: /settings/i }).isVisible().catch(() => false);
    if (adminTitle) logOk('Settings title visible');
    else logIssue('Admin Settings', 'Page title not found');

    const adminErrors = await page.locator('.text-red-500, .text-red-700').allTextContents();
    if (adminErrors.length) logIssue('Admin Settings', `Error text: ${adminErrors.join('; ')}`);
    else logOk('No errors on Admin Settings page');

    // ─── SIDEBAR NAVIGATION ───
    console.log('\n=== SIDEBAR NAVIGATION ===');
    await page.goto('http://localhost:3000/');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1000);

    const navLinks = ['Dashboard', 'Projects', 'Portfolios', 'EPS', 'Resources', 'Calendars', 'Reports', 'Risk', 'OBS', 'Admin'];
    const missingNav: string[] = [];
    for (const link of navLinks) {
      const visible = await page.getByRole('link', { name: link }).isVisible().catch(() => false);
      if (!visible) missingNav.push(link);
    }
    if (missingNav.length === 0) logOk(`All ${navLinks.length} sidebar nav links present`);
    else logIssue('Sidebar', `Missing nav links: ${missingNav.join(', ')}`);

    // Check header
    const logoutBtn = await page.getByRole('button', { name: /logout/i }).isVisible().catch(() => false);
    if (logoutBtn) logOk('Logout button visible in header');
    else logIssue('Header', 'Logout button not found');

    // ─── SUMMARY ───
    console.log('\n========================================');
    console.log(`EXPLORATION COMPLETE: ${issues.length} issues found`);
    console.log('========================================');
    if (issues.length) {
      issues.forEach((issue, i) => console.log(`${i + 1}. ${issue}`));
    }
    console.log('========================================\n');

    // Always pass — this is a discovery test
    expect(true).toBe(true);
  });
});
