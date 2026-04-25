# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: tests/06-schedule.spec.ts >> CPM Scheduling >> run schedule on project with activities
- Location: e2e/tests/06-schedule.spec.ts:4:7

# Error details

```
TimeoutError: page.waitForURL: Timeout 15000ms exceeded.
=========================== logs ===========================
waiting for navigation to "/" until "load"
============================================================
```

# Page snapshot

```yaml
- generic [active] [ref=e1]:
  - main [ref=e2]:
    - navigation [ref=e3]:
      - generic [ref=e5]:
        - generic [ref=e6]: B
        - generic [ref=e7]: Bipros
      - generic [ref=e8]:
        - generic [ref=e9] [cursor=pointer]: Platform
        - generic [ref=e10] [cursor=pointer]: Industries
        - generic [ref=e11] [cursor=pointer]: Customers
        - generic [ref=e12] [cursor=pointer]: Pricing
        - generic [ref=e13] [cursor=pointer]: Resources
      - generic [ref=e14]:
        - button "Sign in" [ref=e15]
        - button "Request demo" [ref=e16]
    - generic [ref=e20]:
      - generic [ref=e21]:
        - generic [ref=e22]:
          - generic [ref=e23]: Enterprise PPM
          - generic [ref=e25]: Live · v4.2
        - heading "Run every infrastructure programme as one ." [level=1] [ref=e27]:
          - text: Run every infrastructure programme
          - text: as
          - generic [ref=e28]:
            - emphasis [ref=e29]: one
            - img [ref=e30]
          - text: .
        - paragraph [ref=e32]: Portfolio, schedule, cost, risk, and field operations on a single spine. Trusted by programme leaders to deliver at scale — from rail corridors to solar farms.
        - generic [ref=e33]:
          - button "Request demo" [ref=e34]:
            - text: Request demo
            - img [ref=e35]
          - button "Explore the platform" [ref=e38]
        - generic [ref=e39]:
          - generic [ref=e40]:
            - generic [ref=e41]: MC
            - generic [ref=e42]: JR
            - generic [ref=e43]: AS
            - generic [ref=e44]: +
          - generic [ref=e45]:
            - generic [ref=e46]:
              - generic [ref=e47]: ★★★★★
              - generic [ref=e48]: 4.8 on G2
              - generic [ref=e49]: ·
              - generic [ref=e50]: 200+ reviews
            - generic [ref=e51]:
              - strong [ref=e52]: 400+ teams
              - text: delivering programmes on Bipros
        - generic [ref=e53]:
          - generic [ref=e54]:
            - generic [ref=e57]: Live · customer portfolios
            - generic [ref=e58]: Synced 00:14 ago
          - generic [ref=e59]:
            - generic [ref=e60]:
              - generic [ref=e61]: Programmes on track
              - generic [ref=e62]: 94.2%
              - generic [ref=e63]:
                - generic [ref=e64]: ↑
                - text: +2.1 pts
              - img [ref=e65]
            - generic [ref=e67]:
              - generic [ref=e68]: Portfolio under mgmt
              - generic [ref=e69]: $42.8B
              - generic [ref=e70]:
                - generic [ref=e71]: ↑
                - text: +$1.2B QoQ
              - img [ref=e72]
            - generic [ref=e74]:
              - generic [ref=e75]: Mean schedule gain
              - generic [ref=e76]: 32%
              - generic [ref=e77]: vs. baseline
              - img [ref=e78]
        - generic [ref=e80]:
          - generic [ref=e81]: Trusted by
          - generic [ref=e82]: Network Rail
          - generic [ref=e83]: Ørsted
          - generic [ref=e84]: Bechtel
          - generic [ref=e85]: AECOM
          - generic [ref=e86]: Skanska
      - generic [ref=e87]:
        - generic [ref=e89]:
          - heading "Welcome back" [level=3] [ref=e90]
          - generic [ref=e91]:
            - img [ref=e92]
            - text: Secure
        - generic [ref=e95]: Sign in to your portfolio
        - generic [ref=e96]:
          - button "Google" [ref=e97]
          - button "Microsoft" [ref=e98]
          - button "SSO" [ref=e99]
        - generic [ref=e100]: or with email
        - generic [ref=e103]: Invalid username or password
        - generic [ref=e104]: Email or username
        - textbox "you@company.com" [ref=e105]: admin
        - generic [ref=e106]: Password
        - generic [ref=e107]:
          - textbox [ref=e108]: admin123
          - button "Show password" [ref=e109]:
            - img [ref=e110]
        - generic [ref=e113]:
          - generic [ref=e114]:
            - checkbox "Remember me" [checked] [ref=e115]
            - text: Remember me
          - generic [ref=e116] [cursor=pointer]: Forgot?
        - button "Sign in" [ref=e117]
        - generic [ref=e118]:
          - generic [ref=e119]: SOC 2
          - generic [ref=e120]: ISO 27001
          - generic [ref=e121]: SSO / SAML
    - generic [ref=e122]:
      - generic [ref=e123]:
        - generic [ref=e124]: The platform
        - heading "One spine for planning, execution, and control" [level=2] [ref=e125]
        - paragraph [ref=e126]: Three disciplines, nine modules, one data model. Replace stitched-together tools with a system designed for how programmes actually run.
      - generic [ref=e127]:
        - generic [ref=e128]:
          - generic [ref=e129]: I · PLAN
          - img [ref=e131]
          - heading "Plan" [level=4] [ref=e134]
          - paragraph [ref=e135]: Portfolios, enterprise project structures, baselines — model programmes the way they're actually funded and reported.
          - generic [ref=e136] [cursor=pointer]: Portfolio, EPS, baselines →
        - generic [ref=e137]:
          - generic [ref=e138]: II · EXECUTE
          - img [ref=e140]
          - heading "Execute" [level=4] [ref=e142]
          - paragraph [ref=e143]: Scheduling, resource levelling, field operations. The only CPM engine built for multi-project portfolios at programme scale.
          - generic [ref=e144] [cursor=pointer]: Scheduling, resources, field →
        - generic [ref=e145]:
          - generic [ref=e146]: III · CONTROL
          - img [ref=e148]
          - heading "Control" [level=4] [ref=e151]
          - paragraph [ref=e152]: Cost, earned-value, risk, variance — one source of truth for the numbers your sponsors and auditors ask for.
          - generic [ref=e153] [cursor=pointer]: Cost, EVM, risk →
    - generic [ref=e154]:
      - generic [ref=e156]:
        - generic [ref=e157]: Nine modules · one model
        - heading "Built for the whole programme" [level=2] [ref=e158]:
          - text: Built for the
          - emphasis [ref=e159]: whole
          - text: programme
        - paragraph [ref=e160]: Every module shares a single data model, so cost reconciles to schedule, risk rolls up to portfolio, and field progress flows back to EVM without export-and-reimport.
      - generic [ref=e161]:
        - generic [ref=e162] [cursor=pointer]:
          - generic [ref=e163]: M01
          - img [ref=e165]
          - heading "Portfolio" [level=5] [ref=e168]
          - paragraph [ref=e169]: Programmes, baselines, funding.
        - generic [ref=e170] [cursor=pointer]:
          - generic [ref=e171]: M02
          - img [ref=e173]
          - heading "Schedule" [level=5] [ref=e175]
          - paragraph [ref=e176]: CPM, levelling, baselines.
        - generic [ref=e177] [cursor=pointer]:
          - generic [ref=e178]: M03
          - img [ref=e180]
          - heading "Cost" [level=5] [ref=e183]
          - paragraph [ref=e184]: Budget, actuals, commitments.
        - generic [ref=e185] [cursor=pointer]:
          - generic [ref=e186]: M04
          - img [ref=e188]
          - heading "Risk" [level=5] [ref=e190]
          - paragraph [ref=e191]: Register, analysis, mitigation.
        - generic [ref=e192] [cursor=pointer]:
          - generic [ref=e193]: M05
          - img [ref=e195]
          - heading "Procurement" [level=5] [ref=e198]
          - paragraph [ref=e199]: Contracts, POs, awards.
        - generic [ref=e200] [cursor=pointer]:
          - generic [ref=e201]: M06
          - img [ref=e203]
          - heading "Field" [level=5] [ref=e208]
          - paragraph [ref=e209]: Daily progress, quantities.
        - generic [ref=e210] [cursor=pointer]:
          - generic [ref=e211]: M07
          - img [ref=e213]
          - heading "Quality" [level=5] [ref=e216]
          - paragraph [ref=e217]: Inspections, NCRs, punch lists.
        - generic [ref=e218] [cursor=pointer]:
          - generic [ref=e219]: M08
          - img [ref=e221]
          - heading "HSE" [level=5] [ref=e223]
          - paragraph [ref=e224]: Incidents, audits, compliance.
        - generic [ref=e225] [cursor=pointer]:
          - generic [ref=e226]: M09
          - img [ref=e228]
          - heading "Resources" [level=5] [ref=e233]
          - paragraph [ref=e234]: People, equipment, rate cards.
    - generic [ref=e235]:
      - generic [ref=e236]:
        - generic [ref=e237]: Built for heavy infrastructure
        - heading "Industries we serve" [level=2] [ref=e238]
      - generic [ref=e239]:
        - generic [ref=e240] [cursor=pointer]:
          - img [ref=e243]
          - generic [ref=e249]:
            - generic [ref=e250]: "01"
            - heading "Road & Rail" [level=5] [ref=e251]
            - paragraph [ref=e252]: Linear mega-projects with corridor-based phasing.
        - generic [ref=e253] [cursor=pointer]:
          - img [ref=e256]
          - generic [ref=e260]:
            - generic [ref=e261]: "02"
            - heading "Energy" [level=5] [ref=e262]
            - paragraph [ref=e263]: Solar, wind, and transmission programmes.
        - generic [ref=e264] [cursor=pointer]:
          - img [ref=e267]
          - generic [ref=e271]:
            - generic [ref=e272]: "03"
            - heading "Water" [level=5] [ref=e273]
            - paragraph [ref=e274]: Dams, treatment, distribution networks.
        - generic [ref=e275] [cursor=pointer]:
          - img [ref=e278]
          - generic [ref=e281]:
            - generic [ref=e282]: "04"
            - heading "Urban Rail" [level=5] [ref=e283]
            - paragraph [ref=e284]: Metro, light rail, and station programmes.
    - generic [ref=e285]:
      - generic [ref=e286]:
        - generic [ref=e287]: $42B
        - generic [ref=e288]: Portfolio value under management
      - generic [ref=e289]:
        - generic [ref=e290]: 1,800+
        - generic [ref=e291]: Projects delivered
      - generic [ref=e292]:
        - generic [ref=e293]: 32%
        - generic [ref=e294]: Schedule improvement
      - generic [ref=e295]:
        - generic [ref=e296]: 99.95%
        - generic [ref=e297]: Platform uptime
    - generic [ref=e298]:
      - generic [ref=e299]:
        - generic [ref=e300]: Showcase · Master schedule
        - heading "Every programme on one Gantt." [level=2] [ref=e301]:
          - text: Every programme on
          - emphasis [ref=e302]: one
          - text: Gantt.
        - paragraph [ref=e303]: Roll 2,000+ activities across 50 projects into a single master schedule. Drive critical-path across the portfolio, not project-by-project.
        - list [ref=e304]:
          - listitem [ref=e305]:
            - img [ref=e306]
            - text: Portfolio-level CPM with resource levelling
          - listitem [ref=e308]:
            - img [ref=e309]
            - text: Baselines per programme, per sponsor, per audit
          - listitem [ref=e311]:
            - img [ref=e312]
            - text: Import from P6, MS Project, Primavera in minutes
          - listitem [ref=e314]:
            - img [ref=e315]
            - text: Variance surfaced against baseline, not version-to-version
        - button "Explore scheduling →" [ref=e317]
      - generic [ref=e318]:
        - generic [ref=e319]:
          - generic [ref=e320]: NORTHWEST RAIL EXT · WBS 1.4.2
          - generic [ref=e321]: JAN · FEB · MAR · APR · MAY · JUN
        - generic [ref=e323]: Earthworks
        - generic [ref=e327]: Bridge sections
        - generic [ref=e331]: Track laying
        - generic [ref=e335]: Signalling
        - generic [ref=e339]: Station fit-out
        - generic [ref=e343]: Testing & commissioning
    - generic [ref=e346]:
      - generic [ref=e347]:
        - generic [ref=e348]: How rollouts happen
        - heading "From kickoff to live programme in weeks." [level=2] [ref=e349]
      - generic [ref=e350]:
        - generic [ref=e352]:
          - generic [ref=e353]: I
          - heading "Configure" [level=5] [ref=e354]
          - paragraph [ref=e355]: WBS, codes, calendars, cost structures.
        - generic [ref=e356]:
          - generic [ref=e357]: II
          - heading "Migrate" [level=5] [ref=e358]
          - paragraph [ref=e359]: Import from P6, MSP, or spreadsheets.
        - generic [ref=e360]:
          - generic [ref=e361]: III
          - heading "Roll out" [level=5] [ref=e362]
          - paragraph [ref=e363]: Train programme managers & controllers.
        - generic [ref=e364]:
          - generic [ref=e365]: IV
          - heading "Operate" [level=5] [ref=e366]
          - paragraph [ref=e367]: Weekly updates, monthly baselines, quarterly reviews.
    - generic [ref=e370]:
      - generic [ref=e371]: Ready when you are
      - heading "Run every programme as one." [level=2] [ref=e372]:
        - text: Run every programme
        - emphasis [ref=e373]: as one.
      - paragraph [ref=e374]: See Bipros EPPM on your own data. Demo in 30 minutes, pilot in two weeks.
      - generic [ref=e375]:
        - button "Request demo →" [ref=e376]
        - button "Download the whitepaper" [ref=e377]
    - generic [ref=e378]:
      - generic [ref=e379]:
        - generic [ref=e380]:
          - generic [ref=e381]: Bipros
          - paragraph [ref=e382]: Enterprise portfolio & project management for heavy infrastructure. Built on one data model, delivered as one platform.
          - generic [ref=e383]:
            - img [ref=e385] [cursor=pointer]
            - img [ref=e388] [cursor=pointer]
            - img [ref=e391] [cursor=pointer]
        - generic [ref=e393]:
          - heading "Product" [level=6] [ref=e394]
          - list [ref=e395]:
            - listitem [ref=e396]: Platform
            - listitem [ref=e397]: Scheduling
            - listitem [ref=e398]: Cost & EVM
            - listitem [ref=e399]: Risk
            - listitem [ref=e400]: Pricing
        - generic [ref=e401]:
          - heading "Industries" [level=6] [ref=e402]
          - list [ref=e403]:
            - listitem [ref=e404]: Road & Rail
            - listitem [ref=e405]: Energy
            - listitem [ref=e406]: Water
            - listitem [ref=e407]: Urban Rail
        - generic [ref=e408]:
          - heading "Resources" [level=6] [ref=e409]
          - list [ref=e410]:
            - listitem [ref=e411]: Customers
            - listitem [ref=e412]: Whitepapers
            - listitem [ref=e413]: Docs
            - listitem [ref=e414]: Webinars
        - generic [ref=e415]:
          - heading "Company" [level=6] [ref=e416]
          - list [ref=e417]:
            - listitem [ref=e418]: About
            - listitem [ref=e419]: Careers
            - listitem [ref=e420]: Partners
            - listitem [ref=e421]: Contact
      - generic [ref=e422]:
        - generic [ref=e423]: © 2026 Bipros. All rights reserved.
        - generic [ref=e424]:
          - generic [ref=e425] [cursor=pointer]: Privacy
          - generic [ref=e426] [cursor=pointer]: Terms
          - generic [ref=e427] [cursor=pointer]: Security
          - generic [ref=e428] [cursor=pointer]: SOC 2
  - button "Open Next.js Dev Tools" [ref=e434] [cursor=pointer]:
    - img [ref=e435]
  - alert [ref=e438]
```

# Test source

```ts
  1  | import { test as base, expect, Page } from '@playwright/test';
  2  | 
  3  | /**
  4  |  * Shared login helper. The login form is now a marketing landing page;
  5  |  * inputs are controlled components without name attributes. We target by
  6  |  * input type and placeholder to stay resilient across CSS tweaks.
  7  |  */
  8  | export async function login(page: Page, username = 'admin', password = 'admin123') {
  9  |   await page.goto('/auth/login');
  10 |   // Username: first text input inside the sign-in card (placeholder "you@company.com")
  11 |   const usernameInput = page.locator('form input[type="text"]').first();
  12 |   await expect(usernameInput).toBeVisible({ timeout: 10_000 });
  13 |   await usernameInput.fill(username);
  14 | 
  15 |   // Password: the password input inside the sign-in card
  16 |   const passwordInput = page.locator('form input[type="password"]').first();
  17 |   await passwordInput.fill(password);
  18 | 
  19 |   await page.locator('form').getByRole('button', { name: /sign in/i }).click();
> 20 |   await page.waitForURL('/', { timeout: 15_000 });
     |              ^ TimeoutError: page.waitForURL: Timeout 15000ms exceeded.
  21 |   await expect(page).toHaveURL('/');
  22 | }
  23 | 
  24 | export const test = base.extend<{ authenticatedPage: Page }>({
  25 |   authenticatedPage: async ({ page }, use) => {
  26 |     await login(page);
  27 |     await use(page);
  28 |   },
  29 | });
  30 | 
  31 | export { expect };
  32 | 
```