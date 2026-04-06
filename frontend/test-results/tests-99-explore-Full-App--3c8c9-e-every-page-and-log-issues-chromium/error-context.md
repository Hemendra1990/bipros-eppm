# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: tests/99-explore.spec.ts >> Full App Exploration >> browse every page and log issues
- Location: e2e/tests/99-explore.spec.ts:17:7

# Error details

```
Test timeout of 30000ms exceeded.
```

```
Error: locator.click: Test timeout of 30000ms exceeded.
Call log:
  - waiting for getByText('Costs').first()

```

# Page snapshot

```yaml
- generic [ref=e1]:
  - generic [ref=e2]:
    - complementary [ref=e3]:
      - generic [ref=e4]:
        - generic [ref=e5]: Bipros
        - button [ref=e6]:
          - img [ref=e7]
      - navigation [ref=e9]:
        - link "Dashboard" [ref=e10] [cursor=pointer]:
          - /url: /
          - img [ref=e11]
          - generic [ref=e16]: Dashboard
        - link "Projects" [ref=e17] [cursor=pointer]:
          - /url: /projects
          - img [ref=e18]
          - generic [ref=e23]: Projects
        - link "Portfolios" [ref=e24] [cursor=pointer]:
          - /url: /portfolios
          - img [ref=e25]
          - generic [ref=e28]: Portfolios
        - link "EPS" [ref=e29] [cursor=pointer]:
          - /url: /eps
          - img [ref=e30]
          - generic [ref=e34]: EPS
        - link "Resources" [active] [ref=e35] [cursor=pointer]:
          - /url: /resources
          - img [ref=e36]
          - generic [ref=e41]: Resources
        - link "Calendars" [ref=e42] [cursor=pointer]:
          - /url: /admin/calendars
          - img [ref=e43]
          - generic [ref=e45]: Calendars
        - link "Reports" [ref=e46] [cursor=pointer]:
          - /url: /reports
          - img [ref=e47]
          - generic [ref=e49]: Reports
        - link "Risk" [ref=e50] [cursor=pointer]:
          - /url: /risk
          - img [ref=e51]
          - generic [ref=e53]: Risk
        - link "OBS" [ref=e54] [cursor=pointer]:
          - /url: /obs
          - img [ref=e55]
          - generic [ref=e60]: OBS
        - link "Admin" [ref=e61] [cursor=pointer]:
          - /url: /admin/settings
          - img [ref=e62]
          - generic [ref=e65]: Admin
    - generic [ref=e66]:
      - banner [ref=e67]:
        - heading "Enterprise Project Portfolio Management" [level=1] [ref=e69]
        - generic [ref=e70]:
          - generic [ref=e71]:
            - img [ref=e72]
            - generic [ref=e75]: System
          - button "Logout" [ref=e76]:
            - img [ref=e77]
            - generic [ref=e80]: Logout
      - main [ref=e81]:
        - generic [ref=e82]:
          - generic [ref=e83]:
            - generic [ref=e84]:
              - heading "Resources" [level=1] [ref=e85]
              - paragraph [ref=e86]: Manage labor, nonlabor, and material resources
            - link "New Resource" [ref=e88] [cursor=pointer]:
              - /url: /resources/new
              - img [ref=e89]
              - text: New Resource
          - table [ref=e91]:
            - rowgroup [ref=e92]:
              - row "Code Name Type Status Max Units/Day Actions" [ref=e93]:
                - columnheader "Code" [ref=e94] [cursor=pointer]:
                  - generic [ref=e95]:
                    - generic [ref=e96]: Code
                    - img [ref=e97]
                - columnheader "Name" [ref=e100] [cursor=pointer]:
                  - generic [ref=e101]:
                    - generic [ref=e102]: Name
                    - img [ref=e103]
                - columnheader "Type" [ref=e106] [cursor=pointer]:
                  - generic [ref=e107]:
                    - generic [ref=e108]: Type
                    - img [ref=e109]
                - columnheader "Status" [ref=e112]:
                  - generic [ref=e114]: Status
                - columnheader "Max Units/Day" [ref=e115] [cursor=pointer]:
                  - generic [ref=e116]:
                    - generic [ref=e117]: Max Units/Day
                    - img [ref=e118]
                - columnheader "Actions" [ref=e121]:
                  - generic [ref=e123]: Actions
            - rowgroup [ref=e124]:
              - row "PM01 Project Manager LABOR ACTIVE 8" [ref=e125]:
                - cell "PM01" [ref=e126]
                - cell "Project Manager" [ref=e127]
                - cell "LABOR" [ref=e128]
                - cell "ACTIVE" [ref=e129]:
                  - generic [ref=e130]: ACTIVE
                - cell "8" [ref=e131]
                - cell [ref=e132]:
                  - button [ref=e133]:
                    - img [ref=e134]
              - row "CE01 Civil Engineer LABOR ACTIVE 8" [ref=e137]:
                - cell "CE01" [ref=e138]
                - cell "Civil Engineer" [ref=e139]
                - cell "LABOR" [ref=e140]
                - cell "ACTIVE" [ref=e141]:
                  - generic [ref=e142]: ACTIVE
                - cell "8" [ref=e143]
                - cell [ref=e144]:
                  - button [ref=e145]:
                    - img [ref=e146]
              - row "SE01 Structural Engineer LABOR ACTIVE 8" [ref=e149]:
                - cell "SE01" [ref=e150]
                - cell "Structural Engineer" [ref=e151]
                - cell "LABOR" [ref=e152]
                - cell "ACTIVE" [ref=e153]:
                  - generic [ref=e154]: ACTIVE
                - cell "8" [ref=e155]
                - cell [ref=e156]:
                  - button [ref=e157]:
                    - img [ref=e158]
              - row "EE01 Electrical Engineer LABOR ACTIVE 8" [ref=e161]:
                - cell "EE01" [ref=e162]
                - cell "Electrical Engineer" [ref=e163]
                - cell "LABOR" [ref=e164]
                - cell "ACTIVE" [ref=e165]:
                  - generic [ref=e166]: ACTIVE
                - cell "8" [ref=e167]
                - cell [ref=e168]:
                  - button [ref=e169]:
                    - img [ref=e170]
              - row "FM01 Construction Foreman LABOR ACTIVE 10" [ref=e173]:
                - cell "FM01" [ref=e174]
                - cell "Construction Foreman" [ref=e175]
                - cell "LABOR" [ref=e176]
                - cell "ACTIVE" [ref=e177]:
                  - generic [ref=e178]: ACTIVE
                - cell "10" [ref=e179]
                - cell [ref=e180]:
                  - button [ref=e181]:
                    - img [ref=e182]
              - row "LB01 General Laborer LABOR ACTIVE 10" [ref=e185]:
                - cell "LB01" [ref=e186]
                - cell "General Laborer" [ref=e187]
                - cell "LABOR" [ref=e188]
                - cell "ACTIVE" [ref=e189]:
                  - generic [ref=e190]: ACTIVE
                - cell "10" [ref=e191]
                - cell [ref=e192]:
                  - button [ref=e193]:
                    - img [ref=e194]
              - row "WD01 Certified Welder LABOR ACTIVE 8" [ref=e197]:
                - cell "WD01" [ref=e198]
                - cell "Certified Welder" [ref=e199]
                - cell "LABOR" [ref=e200]
                - cell "ACTIVE" [ref=e201]:
                  - generic [ref=e202]: ACTIVE
                - cell "8" [ref=e203]
                - cell [ref=e204]:
                  - button [ref=e205]:
                    - img [ref=e206]
              - row "SV01 Land Surveyor LABOR ACTIVE 8" [ref=e209]:
                - cell "SV01" [ref=e210]
                - cell "Land Surveyor" [ref=e211]
                - cell "LABOR" [ref=e212]
                - cell "ACTIVE" [ref=e213]:
                  - generic [ref=e214]: ACTIVE
                - cell "8" [ref=e215]
                - cell [ref=e216]:
                  - button [ref=e217]:
                    - img [ref=e218]
              - row "DEV01 Software Developer LABOR ACTIVE 8" [ref=e221]:
                - cell "DEV01" [ref=e222]
                - cell "Software Developer" [ref=e223]
                - cell "LABOR" [ref=e224]
                - cell "ACTIVE" [ref=e225]:
                  - generic [ref=e226]: ACTIVE
                - cell "8" [ref=e227]
                - cell [ref=e228]:
                  - button [ref=e229]:
                    - img [ref=e230]
              - row "QA01 QA Engineer LABOR ACTIVE 8" [ref=e233]:
                - cell "QA01" [ref=e234]
                - cell "QA Engineer" [ref=e235]
                - cell "LABOR" [ref=e236]
                - cell "ACTIVE" [ref=e237]:
                  - generic [ref=e238]: ACTIVE
                - cell "8" [ref=e239]
                - cell [ref=e240]:
                  - button [ref=e241]:
                    - img [ref=e242]
              - row "CR01 Tower Crane 50T NONLABOR ACTIVE 10" [ref=e245]:
                - cell "CR01" [ref=e246]
                - cell "Tower Crane 50T" [ref=e247]
                - cell "NONLABOR" [ref=e248]
                - cell "ACTIVE" [ref=e249]:
                  - generic [ref=e250]: ACTIVE
                - cell "10" [ref=e251]
                - cell [ref=e252]:
                  - button [ref=e253]:
                    - img [ref=e254]
              - row "EX01 Hydraulic Excavator NONLABOR ACTIVE 10" [ref=e257]:
                - cell "EX01" [ref=e258]
                - cell "Hydraulic Excavator" [ref=e259]
                - cell "NONLABOR" [ref=e260]
                - cell "ACTIVE" [ref=e261]:
                  - generic [ref=e262]: ACTIVE
                - cell "10" [ref=e263]
                - cell [ref=e264]:
                  - button [ref=e265]:
                    - img [ref=e266]
              - row "BD01 D6 Bulldozer NONLABOR ACTIVE 10" [ref=e269]:
                - cell "BD01" [ref=e270]
                - cell "D6 Bulldozer" [ref=e271]
                - cell "NONLABOR" [ref=e272]
                - cell "ACTIVE" [ref=e273]:
                  - generic [ref=e274]: ACTIVE
                - cell "10" [ref=e275]
                - cell [ref=e276]:
                  - button [ref=e277]:
                    - img [ref=e278]
              - row "PU01 Concrete Pump Truck NONLABOR ACTIVE 8" [ref=e281]:
                - cell "PU01" [ref=e282]
                - cell "Concrete Pump Truck" [ref=e283]
                - cell "NONLABOR" [ref=e284]
                - cell "ACTIVE" [ref=e285]:
                  - generic [ref=e286]: ACTIVE
                - cell "8" [ref=e287]
                - cell [ref=e288]:
                  - button [ref=e289]:
                    - img [ref=e290]
              - row "MAT-CON Ready-Mix Concrete (m3) MATERIAL ACTIVE 100" [ref=e293]:
                - cell "MAT-CON" [ref=e294]
                - cell "Ready-Mix Concrete (m3)" [ref=e295]
                - cell "MATERIAL" [ref=e296]
                - cell "ACTIVE" [ref=e297]:
                  - generic [ref=e298]: ACTIVE
                - cell "100" [ref=e299]
                - cell [ref=e300]:
                  - button [ref=e301]:
                    - img [ref=e302]
              - row "MAT-STL Structural Steel (tons) MATERIAL ACTIVE 50" [ref=e305]:
                - cell "MAT-STL" [ref=e306]
                - cell "Structural Steel (tons)" [ref=e307]
                - cell "MATERIAL" [ref=e308]
                - cell "ACTIVE" [ref=e309]:
                  - generic [ref=e310]: ACTIVE
                - cell "50" [ref=e311]
                - cell [ref=e312]:
                  - button [ref=e313]:
                    - img [ref=e314]
              - row "MAT-REB Rebar Grade 60 (tons) MATERIAL ACTIVE 30" [ref=e317]:
                - cell "MAT-REB" [ref=e318]
                - cell "Rebar Grade 60 (tons)" [ref=e319]
                - cell "MATERIAL" [ref=e320]
                - cell "ACTIVE" [ref=e321]:
                  - generic [ref=e322]: ACTIVE
                - cell "30" [ref=e323]
                - cell [ref=e324]:
                  - button [ref=e325]:
                    - img [ref=e326]
              - row "MAT-LUM Formwork Lumber (bf) MATERIAL ACTIVE 500" [ref=e329]:
                - cell "MAT-LUM" [ref=e330]
                - cell "Formwork Lumber (bf)" [ref=e331]
                - cell "MATERIAL" [ref=e332]
                - cell "ACTIVE" [ref=e333]:
                  - generic [ref=e334]: ACTIVE
                - cell "500" [ref=e335]
                - cell [ref=e336]:
                  - button [ref=e337]:
                    - img [ref=e338]
  - button "Open Next.js Dev Tools" [ref=e346] [cursor=pointer]:
    - img [ref=e347]
  - alert [ref=e350]
```

# Test source

```ts
  138 | 
  139 |     await expect(page.getByRole('heading', { name: 'New Project' })).toBeVisible();
  140 |     logOk('New Project form title visible');
  141 | 
  142 |     const formFields = ['code', 'name', 'description', 'epsNodeId', 'plannedStartDate', 'plannedFinishDate', 'priority'];
  143 |     const missingFields: string[] = [];
  144 |     for (const f of formFields) {
  145 |       const visible = await page.locator(`[name="${f}"]`).isVisible().catch(() => false);
  146 |       if (!visible) missingFields.push(f);
  147 |     }
  148 |     if (missingFields.length === 0) logOk(`All ${formFields.length} form fields visible`);
  149 |     else logIssue('New Project', `Missing form fields: ${missingFields.join(', ')}`);
  150 | 
  151 |     // Check EPS dropdown has options
  152 |     const epsOpts = await page.locator('[name="epsNodeId"] option').count();
  153 |     if (epsOpts > 1) logOk(`EPS dropdown has ${epsOpts} options`);
  154 |     else logIssue('New Project', `EPS dropdown has only ${epsOpts} options — no EPS nodes available to select`);
  155 | 
  156 |     const createProjBtn = await page.getByRole('button', { name: /create project/i }).isVisible().catch(() => false);
  157 |     if (createProjBtn) logOk('Create Project button visible');
  158 |     else logIssue('New Project', 'Create Project button not found');
  159 | 
  160 |     // ─── PROJECT DETAIL ───
  161 |     console.log('\n=== PROJECT DETAIL ===');
  162 |     await page.goto('http://localhost:3000/projects');
  163 |     await page.waitForLoadState('networkidle');
  164 |     await page.waitForTimeout(1000);
  165 | 
  166 |     const firstProjLink = page.locator('table tbody tr a').first();
  167 |     if (await firstProjLink.isVisible().catch(() => false)) {
  168 |       const name = await firstProjLink.textContent();
  169 |       await firstProjLink.click();
  170 |       await page.waitForURL(/\/projects\//, { timeout: 10000 });
  171 |       await page.waitForTimeout(1500);
  172 |       logOk(`Opened project: ${name?.trim()}`);
  173 | 
  174 |       // Check project name heading
  175 |       const projHeading = await page.getByRole('heading', { level: 1 }).first().textContent();
  176 |       if (projHeading && projHeading.length > 0) logOk(`Project heading: "${projHeading}"`);
  177 |       else logIssue('Project Detail', 'No project name heading');
  178 | 
  179 |       // Check tabs
  180 |       const tabs = ['Overview', 'WBS', 'Activities', 'Gantt', 'Resources', 'Costs', 'EVM'];
  181 |       const missingTabs: string[] = [];
  182 |       for (const tab of tabs) {
  183 |         const visible = await page.getByText(tab).first().isVisible().catch(() => false);
  184 |         if (!visible) missingTabs.push(tab);
  185 |       }
  186 |       if (missingTabs.length === 0) logOk(`All ${tabs.length} tabs visible`);
  187 |       else logIssue('Project Detail', `Missing tabs: ${missingTabs.join(', ')}`);
  188 | 
  189 |       // Check Overview tab content
  190 |       const overviewContent = await page.locator('main').textContent();
  191 |       if (overviewContent && overviewContent.length > 50) logOk('Overview tab has content');
  192 |       else logIssue('Project Detail', 'Overview tab appears empty');
  193 | 
  194 |       // ─── WBS TAB ───
  195 |       console.log('\n  --- WBS TAB ---');
  196 |       await page.getByText('WBS').first().click();
  197 |       await page.waitForTimeout(1500);
  198 |       const wbsContent = await page.locator('main').textContent();
  199 |       const wbsErrors = await page.locator('.text-red-500, .text-red-700').allTextContents();
  200 |       if (wbsErrors.length) logIssue('WBS Tab', `Error text: ${wbsErrors.join('; ')}`);
  201 |       else if (wbsContent && wbsContent.includes('WBS')) logOk('WBS tab has content');
  202 |       else logIssue('WBS Tab', 'WBS tab appears empty or no WBS nodes');
  203 | 
  204 |       // ─── ACTIVITIES TAB ───
  205 |       console.log('\n  --- ACTIVITIES TAB ---');
  206 |       await page.getByText('Activities').first().click();
  207 |       await page.waitForTimeout(1500);
  208 |       const actTables = await page.locator('table').count();
  209 |       const newActBtn = await page.getByRole('button', { name: /new activity/i }).isVisible().catch(() => false);
  210 |       if (actTables > 0 || newActBtn) logOk(`Activities tab: tables=${actTables}, New Activity button=${newActBtn}`);
  211 |       else logIssue('Activities Tab', 'No table or New Activity button found');
  212 |       
  213 |       const actErrors = await page.locator('.text-red-500, .text-red-700').allTextContents();
  214 |       if (actErrors.length) logIssue('Activities Tab', `Error text: ${actErrors.join('; ')}`);
  215 | 
  216 |       // ─── GANTT TAB ───
  217 |       console.log('\n  --- GANTT TAB ---');
  218 |       await page.getByText('Gantt').first().click();
  219 |       await page.waitForTimeout(2000);
  220 |       const svgCount = await page.locator('svg').count();
  221 |       const canvasCount = await page.locator('canvas').count();
  222 |       if (svgCount > 0 || canvasCount > 0) logOk(`Gantt rendered: ${svgCount} SVG, ${canvasCount} canvas elements`);
  223 |       else logIssue('Gantt Tab', 'No SVG or canvas elements — Gantt chart not rendered');
  224 | 
  225 |       const ganttErrors = await page.locator('.text-red-500, .text-red-700').allTextContents();
  226 |       if (ganttErrors.length) logIssue('Gantt Tab', `Error text: ${ganttErrors.join('; ')}`);
  227 | 
  228 |       // ─── RESOURCES TAB ───
  229 |       console.log('\n  --- RESOURCES TAB ---');
  230 |       await page.getByText('Resources').first().click();
  231 |       await page.waitForTimeout(1500);
  232 |       const resTabContent = await page.locator('main').textContent();
  233 |       if (resTabContent && resTabContent.length > 20) logOk('Resources tab has content');
  234 |       else logIssue('Resources Tab', 'Resources tab appears empty');
  235 | 
  236 |       // ─── COSTS TAB ───
  237 |       console.log('\n  --- COSTS TAB ---');
> 238 |       await page.getByText('Costs').first().click();
      |                                             ^ Error: locator.click: Test timeout of 30000ms exceeded.
  239 |       await page.waitForTimeout(1500);
  240 |       const costErrors = await page.locator('.text-red-500, .text-red-700').allTextContents();
  241 |       if (costErrors.length) logIssue('Costs Tab', `Error text: ${costErrors.join('; ')}`);
  242 |       else logOk('Costs tab loaded without visible errors');
  243 | 
  244 |       // ─── EVM TAB ───
  245 |       console.log('\n  --- EVM TAB ---');
  246 |       await page.getByText('EVM').first().click();
  247 |       await page.waitForTimeout(2000);
  248 |       const evmErrors = await page.locator('.text-red-500, .text-red-700').allTextContents();
  249 |       if (evmErrors.length) logIssue('EVM Tab', `Error text: ${evmErrors.join('; ')}`);
  250 |       else logOk('EVM tab loaded without visible errors');
  251 | 
  252 |       const evmCharts = await page.locator('svg').count();
  253 |       if (evmCharts > 0) logOk(`EVM has ${evmCharts} SVG chart elements`);
  254 |       else logIssue('EVM Tab', 'No charts rendered on EVM tab');
  255 |     }
  256 | 
  257 |     // ─── RESOURCES PAGE ───
  258 |     console.log('\n=== RESOURCES PAGE (/resources) ===');
  259 |     await page.goto('http://localhost:3000/resources');
  260 |     await page.waitForLoadState('networkidle');
  261 |     await page.waitForTimeout(1500);
  262 | 
  263 |     await expect(page.getByRole('heading', { name: 'Resources' })).toBeVisible();
  264 |     logOk('Resources title visible');
  265 | 
  266 |     const resRows = await page.locator('table tbody tr').count();
  267 |     if (resRows > 0) logOk(`Resources table has ${resRows} rows`);
  268 |     else logIssue('Resources', 'Table has 0 rows');
  269 | 
  270 |     const resHeaders = await page.locator('table thead th').allTextContents();
  271 |     logOk(`Resource headers: ${resHeaders.join(', ')}`);
  272 | 
  273 |     const newResLink = await page.getByRole('link', { name: /new resource/i }).isVisible().catch(() => false);
  274 |     if (newResLink) logOk('New Resource link visible');
  275 |     else logIssue('Resources', 'New Resource link not found');
  276 | 
  277 |     // Check resource types displayed
  278 |     const labor = await page.getByText('LABOR').isVisible().catch(() => false);
  279 |     const nonLabor = await page.getByText('NONLABOR').isVisible().catch(() => false);
  280 |     const material = await page.getByText('MATERIAL').isVisible().catch(() => false);
  281 |     logOk(`Resource types: LABOR=${labor} NONLABOR=${nonLabor} MATERIAL=${material}`);
  282 | 
  283 |     // ─── NEW RESOURCE FORM ───
  284 |     console.log('\n=== NEW RESOURCE FORM (/resources/new) ===');
  285 |     await page.goto('http://localhost:3000/resources/new');
  286 |     await page.waitForLoadState('networkidle');
  287 |     await page.waitForTimeout(1000);
  288 | 
  289 |     const newResTitle = await page.getByRole('heading', { name: /new resource/i }).isVisible().catch(() => false);
  290 |     if (newResTitle) logOk('New Resource title visible');
  291 |     else logIssue('New Resource', 'Page title not found');
  292 | 
  293 |     const resFormFields = ['code', 'name', 'resourceType', 'maxUnitsPerDay'];
  294 |     const missingResFields: string[] = [];
  295 |     for (const f of resFormFields) {
  296 |       const visible = await page.locator(`[name="${f}"]`).isVisible().catch(() => false);
  297 |       if (!visible) missingResFields.push(f);
  298 |     }
  299 |     if (missingResFields.length === 0) logOk('All resource form fields visible');
  300 |     else logIssue('New Resource', `Missing fields: ${missingResFields.join(', ')}`);
  301 | 
  302 |     // ─── CALENDARS ───
  303 |     console.log('\n=== CALENDARS PAGE (/admin/calendars) ===');
  304 |     await page.goto('http://localhost:3000/admin/calendars');
  305 |     await page.waitForLoadState('networkidle');
  306 |     await page.waitForTimeout(1500);
  307 | 
  308 |     const calTitle = await page.getByRole('heading', { name: /calendar/i }).isVisible().catch(() => false);
  309 |     if (calTitle) logOk('Calendars title visible');
  310 |     else logIssue('Calendars', 'Page title not found');
  311 | 
  312 |     const calRows = await page.locator('table tbody tr').count();
  313 |     logOk(`Calendar rows: ${calRows}`);
  314 | 
  315 |     const calErrors = await page.locator('.text-red-500, .text-red-700').allTextContents();
  316 |     if (calErrors.length) logIssue('Calendars', `Error text: ${calErrors.join('; ')}`);
  317 |     else logOk('No errors on Calendars page');
  318 | 
  319 |     const newCalLink = await page.getByRole('link', { name: /new calendar/i }).isVisible().catch(() => false);
  320 |     const newCalBtn = await page.getByRole('button', { name: /new calendar/i }).isVisible().catch(() => false);
  321 |     if (newCalLink || newCalBtn) logOk('New Calendar link/button visible');
  322 |     else logIssue('Calendars', 'No New Calendar action found');
  323 | 
  324 |     // ─── PORTFOLIOS ───
  325 |     console.log('\n=== PORTFOLIOS PAGE (/portfolios) ===');
  326 |     await page.goto('http://localhost:3000/portfolios');
  327 |     await page.waitForLoadState('networkidle');
  328 |     await page.waitForTimeout(1500);
  329 | 
  330 |     const portTitle = await page.getByRole('heading', { name: /portfolio/i }).isVisible().catch(() => false);
  331 |     if (portTitle) logOk('Portfolios title visible');
  332 |     else logIssue('Portfolios', 'Page title not found');
  333 | 
  334 |     const portErrors = await page.locator('.text-red-500, .text-red-700').allTextContents();
  335 |     if (portErrors.length) logIssue('Portfolios', `Error text: ${portErrors.join('; ')}`);
  336 |     else logOk('No errors on Portfolios page');
  337 | 
  338 |     // ─── RISK ───
```