"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import toast from "react-hot-toast";
import { authApi } from "@/lib/api/authApi";
import { useAuthStore } from "@/lib/state/store";
import "./landing.css";

export default function LoginPage() {
  const router = useRouter();
  const { setAuth } = useAuthStore();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setLoading(true);

    try {
      const result = await authApi.login({ username, password });
      if (result.data) {
        localStorage.setItem("access_token", result.data.accessToken);
        localStorage.setItem("refresh_token", result.data.refreshToken);
        document.cookie = `access_token=${result.data.accessToken}; path=/; max-age=3600`;
        const userResult = await authApi.me();
        if (userResult.data) {
          setAuth(userResult.data, result.data.accessToken, result.data.refreshToken);
          toast.success("Welcome back!");
          router.push("/");
        } else {
          setAuth(
            { id: "", username, email: "", firstName: "", lastName: "", enabled: true, roles: [] },
            result.data.accessToken,
            result.data.refreshToken,
          );
          toast.success("Logged in successfully!");
          router.push("/");
        }
      } else if (result.error) {
        const msg = result.error.message || "Login failed";
        setError(msg);
        toast.error(msg);
      }
    } catch {
      const msg = "Invalid username or password";
      setError(msg);
      toast.error(msg);
    } finally {
      setLoading(false);
    }
  };

  const ssoNotAvailable = () => toast("SSO sign-in is not yet configured.", { icon: "🔒" });

  return (
    <div className="eppm-landing">
      {/* ───────── NAV ───────── */}
      <header className="nav">
        <div className="container nav-inner">
          <a href="#" className="brand" aria-label="Bipros EPPM home">
            <span className="brand-mark">
              <img src="/bipros-logo.png" alt="" />
            </span>
            <span>
              Bipros<span className="e">EPPM</span>
            </span>
          </a>
          <div className="nav-cta">
            <a href="#signin" className="btn btn-ghost btn-sm">Sign in</a>
            <a href="#signin" className="btn btn-primary btn-sm">Get started</a>
          </div>
        </div>
      </header>

      {/* ───────── HERO ───────── */}
      <section className="hero">
        <div className="container hero-inner">
          <div>
            <h1>
              Run every <span className="accent-2">infrastructure project</span> from plan to pavement.
            </h1>
            <p className="hero-sub">
              Bipros EPPM unifies schedules, cost, risk, resources and field progress across road,
              energy, rail and water portfolios — so program directors and site engineers work from
              the same source of truth.
            </p>
            <div className="hero-ctas">
              <a href="#signin" className="btn btn-primary btn-lg">Sign in to your workspace →</a>
              <a href="#signin" className="btn btn-ghost btn-lg">Create an account</a>
            </div>
            <div className="hero-meta">
              <div className="avatars">
                <span className="av">RK</span>
                <span className="av">AV</span>
                <span className="av">MP</span>
                <span className="av">SJ</span>
              </div>
              <span>Trusted by 400+ program teams</span>
              <span className="rating">
                <span className="stars">★★★★★</span> 4.8 on G2
              </span>
            </div>
          </div>

          {/* Sign in card */}
          <div className="signin" id="signin">
            <div className="signin-head">
              <span className="brand-mark" style={{ width: 40, height: 40, borderRadius: 10 }}>
                <img src="/bipros-logo.png" alt="" />
              </span>
              <div>
                <div className="si-title">Sign in to Bipros EPPM</div>
                <div className="si-sub">Welcome back. Enter your credentials to continue.</div>
              </div>
            </div>

            <div className="sso-row">
              <button type="button" className="sso" onClick={ssoNotAvailable}>
                <span className="g">G</span>Google
              </button>
              <button type="button" className="sso" onClick={ssoNotAvailable}>
                <span className="m">⊞</span>Microsoft
              </button>
              <button type="button" className="sso" onClick={ssoNotAvailable}>
                <span className="s">◆</span>SSO
              </button>
            </div>
            <div className="divider"><span>or continue with email</span></div>

            {error && <div className="si-alert">{error}</div>}

            <form className="si-form" onSubmit={handleSubmit}>
              <label className="field">
                <span className="lbl">Username or email</span>
                <input
                  type="text"
                  name="username"
                  placeholder="admin"
                  autoComplete="username"
                  required
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                />
              </label>
              <label className="field">
                <span className="lbl">
                  Password
                  <a href="#" className="forgot" onClick={(e) => e.preventDefault()}>Forgot?</a>
                </span>
                <div className="pw-wrap">
                  <input
                    type={showPassword ? "text" : "password"}
                    name="password"
                    placeholder="••••••••••••"
                    autoComplete="current-password"
                    required
                    minLength={6}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                  />
                  <button
                    type="button"
                    className="pw-toggle"
                    aria-label={showPassword ? "Hide password" : "Show password"}
                    onClick={() => setShowPassword((s) => !s)}
                  >
                    {showPassword ? "Hide" : "Show"}
                  </button>
                </div>
              </label>
              <label className="remember">
                <input type="checkbox" defaultChecked />
                <span>Keep me signed in on this device</span>
              </label>
              <button
                type="submit"
                className="btn btn-primary btn-lg submit"
                style={{ width: "100%" }}
                disabled={loading}
              >
                {loading ? "Signing in…" : "Sign in"}
              </button>
            </form>

            <div className="si-foot">
              New to Bipros? <a href="#" onClick={(e) => e.preventDefault()}>Create a workspace</a> ·{" "}
              <a href="#" onClick={(e) => e.preventDefault()}>Request demo</a>
            </div>
            <div className="si-trust">
              <span>🔒 SOC 2 Type II</span><span>·</span><span>ISO 27001</span><span>·</span><span>SSO &amp; SAML</span>
            </div>
          </div>
        </div>
      </section>

      {/* ───────── PILLARS ───────── */}
      <section className="block" id="platform">
        <div className="container">
          <div className="section-head">
            <div className="eyebrow">The platform</div>
            <h2>One system of record for the whole portfolio.</h2>
            <p>
              Bipros replaces the spreadsheet-and-email sprawl that slows down capital projects —
              from Board reporting to the last mile in the field.
            </p>
          </div>
          <div className="pillars">
            <div className="pillar">
              <div className="p-ico">◆</div>
              <h3>Plan with confidence</h3>
              <p>
                Top-down portfolio planning, bottom-up estimating, and what-if scenarios on a single
                timeline — so funding decisions stop living in slide decks.
              </p>
            </div>
            <div className="pillar plum">
              <div className="p-ico">◈</div>
              <h3>Execute without drift</h3>
              <p>
                Critical-path schedules, resource loading, progress claims and change orders stay
                linked. When the site moves, the plan moves with it.
              </p>
            </div>
            <div className="pillar amber">
              <div className="p-ico">◉</div>
              <h3>Control with evidence</h3>
              <p>
                Earned-value, risk-adjusted forecasts and audit-ready records for every approval.
                Stakeholders see the same numbers you do.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* ───────── MODULES ───────── */}
      <section className="block modules" id="modules">
        <div className="container">
          <div className="section-head">
            <div className="eyebrow">Modules</div>
            <h2>Nine integrated modules. One data model.</h2>
            <p>
              Deploy what you need today. Every module shares the same WBS, cost codes and resource
              pool — so there&rsquo;s no reconciliation tax tomorrow.
            </p>
          </div>
          <div className="mod-grid">
            <div className="mod">
              <div className="m-top"><div className="m-ico">◇</div><div className="m-num">01</div></div>
              <h3>Portfolio &amp; Program</h3>
              <p>Strategic goals, funding envelopes and program-level dashboards for the PMO and Board.</p>
              <div className="tags">
                <span className="tag">Capex pipeline</span>
                <span className="tag">Scenario</span>
                <span className="tag">KPIs</span>
              </div>
            </div>
            <div className="mod">
              <div className="m-top"><div className="m-ico">⧗</div><div className="m-num">02</div></div>
              <h3>Schedule &amp; Planning</h3>
              <p>P6-compatible CPM, baselines, look-ahead plans and multi-project resource levelling.</p>
              <div className="tags">
                <span className="tag">CPM</span>
                <span className="tag">Baselines</span>
                <span className="tag">P6 import</span>
              </div>
            </div>
            <div className="mod">
              <div className="m-top"><div className="m-ico">₹</div><div className="m-num">03</div></div>
              <h3>Cost &amp; Budget</h3>
              <p>Budget → commitment → actuals with earned value, cash-flow and forecast-at-completion.</p>
              <div className="tags">
                <span className="tag">EVM</span>
                <span className="tag">Cashflow</span>
                <span className="tag">EAC</span>
              </div>
            </div>
            <div className="mod">
              <div className="m-top"><div className="m-ico">⚠</div><div className="m-num">04</div></div>
              <h3>Risk &amp; Issues</h3>
              <p>Qualitative and Monte-Carlo risk, mitigation actions and risk-loaded schedules.</p>
              <div className="tags">
                <span className="tag">Monte Carlo</span>
                <span className="tag">Heat map</span>
              </div>
            </div>
            <div className="mod">
              <div className="m-top"><div className="m-ico">⚒</div><div className="m-num">05</div></div>
              <h3>Procurement &amp; Contracts</h3>
              <p>RFQs, vendor evaluation, contracts, amendments and milestone-linked payments.</p>
              <div className="tags">
                <span className="tag">RFQ</span>
                <span className="tag">Contracts</span>
              </div>
            </div>
            <div className="mod">
              <div className="m-top"><div className="m-ico">◎</div><div className="m-num">06</div></div>
              <h3>Field &amp; Daily Reports</h3>
              <p>Offline-first mobile for crews — daily progress, quantities, weather, photos and signatures.</p>
              <div className="tags">
                <span className="tag">Offline</span>
                <span className="tag">Geo-tagged</span>
              </div>
            </div>
            <div className="mod">
              <div className="m-top"><div className="m-ico">✓</div><div className="m-num">07</div></div>
              <h3>Quality &amp; Inspections</h3>
              <p>ITPs, punch lists and non-conformances linked to the WBS and contract milestones.</p>
              <div className="tags">
                <span className="tag">ITP</span>
                <span className="tag">NCR</span>
              </div>
            </div>
            <div className="mod">
              <div className="m-top"><div className="m-ico">⛑</div><div className="m-num">08</div></div>
              <h3>HSE &amp; Compliance</h3>
              <p>Permits to work, toolbox talks, incident reporting and statutory compliance registers.</p>
              <div className="tags">
                <span className="tag">PTW</span>
                <span className="tag">Incidents</span>
              </div>
            </div>
            <div className="mod">
              <div className="m-top"><div className="m-ico">⎔</div><div className="m-num">09</div></div>
              <h3>Resources &amp; Assets</h3>
              <p>Crews, equipment, materials — utilisation across the portfolio, not just one job.</p>
              <div className="tags">
                <span className="tag">Crews</span>
                <span className="tag">Plant</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ───────── INDUSTRIES ───────── */}
      <section className="block industries" id="industries">
        <div className="container">
          <div className="section-head">
            <div className="eyebrow">Built for the field</div>
            <h2>Industries we serve.</h2>
            <p>
              Bipros EPPM ships with pre-configured WBS templates, cost codes, inspection checklists
              and KPIs for the sectors where we work hardest.
            </p>
          </div>
          <div className="ind-grid">
            <article className="ind road">
              <svg className="i-illo" viewBox="0 0 88 88" fill="none" aria-hidden="true">
                <path d="M20 80 L38 10 L50 10 L68 80 Z" stroke="rgba(255,255,255,0.6)" strokeWidth="2" />
                <path d="M44 10 L44 80" stroke="rgba(245,158,11,0.9)" strokeWidth="2" strokeDasharray="6 6" />
              </svg>
              <div className="i-body">
                <div className="i-tag">01 · ROAD &amp; HIGHWAY</div>
                <h3>Road &amp; Transport</h3>
                <p>Chainage-based progress, BOQ billing, toll-corridor packages.</p>
              </div>
            </article>
            <article className="ind energy">
              <svg className="i-illo" viewBox="0 0 88 88" fill="none" aria-hidden="true">
                <path d="M44 8 L32 46 L44 44 L36 80 L58 38 L46 40 Z" stroke="rgba(255,255,255,0.7)" strokeWidth="2" />
              </svg>
              <div className="i-body">
                <div className="i-tag">02 · ENERGY</div>
                <h3>Energy &amp; Utilities</h3>
                <p>Solar, wind, substation and transmission line programs.</p>
              </div>
            </article>
            <article className="ind water">
              <svg className="i-illo" viewBox="0 0 88 88" fill="none" aria-hidden="true">
                <path d="M14 50 Q26 38 38 50 T62 50 T86 50" stroke="rgba(255,255,255,0.7)" strokeWidth="2" fill="none" />
                <path d="M14 64 Q26 52 38 64 T62 64 T86 64" stroke="rgba(255,255,255,0.4)" strokeWidth="2" fill="none" />
                <circle cx="44" cy="24" r="10" stroke="rgba(14,165,166,0.9)" strokeWidth="2" />
              </svg>
              <div className="i-body">
                <div className="i-tag">03 · WATER</div>
                <h3>Water &amp; Wastewater</h3>
                <p>Treatment plants, pipeline networks, irrigation schemes.</p>
              </div>
            </article>
            <article className="ind rail">
              <svg className="i-illo" viewBox="0 0 88 88" fill="none" aria-hidden="true">
                <path d="M10 78 L34 14 M78 78 L54 14" stroke="rgba(255,255,255,0.7)" strokeWidth="2" />
                <path d="M14 60 L74 60 M18 44 L70 44 M24 28 L64 28" stroke="rgba(245,158,11,0.9)" strokeWidth="2" />
              </svg>
              <div className="i-body">
                <div className="i-tag">04 · RAIL &amp; METRO</div>
                <h3>Rail &amp; Metro</h3>
                <p>Alignments, stations, rolling-stock depots and viaducts.</p>
              </div>
            </article>
          </div>
        </div>
      </section>

      {/* ───────── STATS ───────── */}
      <section className="stats">
        <div className="container stat-grid">
          <div className="stat">
            <div className="v">
              ₹ 42,000<span style={{ fontSize: 26, verticalAlign: 6 }}> Cr</span>
            </div>
            <div className="l">Portfolio value managed</div>
          </div>
          <div className="stat">
            <div className="v">1,800+</div>
            <div className="l">Active projects</div>
          </div>
          <div className="stat">
            <div className="v">
              32<span style={{ fontSize: 26, verticalAlign: 6 }}>%</span>
            </div>
            <div className="l">Avg reduction in schedule slip</div>
          </div>
          <div className="stat">
            <div className="v">
              99.95<span style={{ fontSize: 26, verticalAlign: 6 }}>%</span>
            </div>
            <div className="l">Platform uptime</div>
          </div>
        </div>
      </section>

      {/* ───────── SHOWCASE ───────── */}
      <section className="block showcase">
        <div className="container sc-grid">
          <div>
            <div className="eyebrow">Master schedule</div>
            <h2>See the whole program on one timeline.</h2>
            <p style={{ marginTop: 18, fontSize: 16 }}>
              Roll packages up, drill a milestone down. Critical path, float and baseline variance
              are always one click away — no exports, no reconciliation.
            </p>
            <ul className="checks">
              <li><span className="ck">✓</span><span>Multi-project critical path with inter-project links</span></li>
              <li><span className="ck">✓</span><span>Resource-loaded schedules with auto-levelling</span></li>
              <li><span className="ck">✓</span><span>Baseline vs. actual vs. risk-adjusted forecast side-by-side</span></li>
              <li><span className="ck">✓</span><span>Import from P6, MS Project and Excel in one step</span></li>
            </ul>
            <div style={{ marginTop: 28 }}>
              <a href="#" className="btn btn-dark" onClick={(e) => e.preventDefault()}>
                Explore the scheduler →
              </a>
            </div>
          </div>

          <div className="mock-shot">
            <div className="sc-head">
              <span className="brand-mark" style={{ width: 22, height: 22 }}>
                <img src="/bipros-logo.png" alt="" />
              </span>
              NH-44 · Master schedule
              <div className="tabs"><span>Week</span><span className="on">Month</span><span>Qtr</span></div>
            </div>
            <div className="sc-body">
              <div className="sc-timeline">
                <div>JAN</div><div>FEB</div><div>MAR</div><div>APR</div><div>MAY</div>
              </div>
              <div className="gantt">
                <div className="row">
                  <div className="r-name"><span className="sq" style={{ background: "#1E4FD8" }}></span>Survey &amp; design</div>
                  <div className="r-track"><div className="r-bar" style={{ left: "2%", width: "20%", background: "#1E4FD8" }}></div></div>
                </div>
                <div className="row">
                  <div className="r-name"><span className="sq" style={{ background: "#6B3FA0" }}></span>Land acquisition</div>
                  <div className="r-track"><div className="r-bar" style={{ left: "12%", width: "28%", background: "#6B3FA0" }}></div></div>
                </div>
                <div className="row">
                  <div className="r-name"><span className="sq" style={{ background: "#F59E0B" }}></span>Earthworks</div>
                  <div className="r-track"><div className="r-bar" style={{ left: "28%", width: "32%", background: "#F59E0B" }}></div></div>
                </div>
                <div className="row">
                  <div className="r-name"><span className="sq" style={{ background: "#0EA5A6" }}></span>Structures</div>
                  <div className="r-track"><div className="r-bar" style={{ left: "42%", width: "28%", background: "#0EA5A6" }}></div></div>
                </div>
                <div className="row">
                  <div className="r-name"><span className="sq" style={{ background: "#1E4FD8" }}></span>Pavement</div>
                  <div className="r-track"><div className="r-bar" style={{ left: "60%", width: "24%", background: "#1E4FD8" }}></div></div>
                </div>
                <div className="row">
                  <div className="r-name"><span className="sq" style={{ background: "#16A34A" }}></span>Finishes &amp; snag</div>
                  <div className="r-track"><div className="r-bar" style={{ left: "78%", width: "18%", background: "#16A34A" }}></div></div>
                </div>
                <div className="row">
                  <div className="r-name" style={{ color: "#B45309" }}><span className="sq" style={{ background: "#DC2626" }}></span>Handover ▲</div>
                  <div className="r-track"><div className="r-bar" style={{ left: "92%", width: "6%", background: "#DC2626" }}></div></div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ───────── WORKFLOW ───────── */}
      <section className="block workflow">
        <div className="container">
          <div className="section-head">
            <div className="eyebrow">How teams roll it out</div>
            <h2>From kickoff to first report in 30 days.</h2>
            <p>A guided onboarding built around capital-project reality — not generic SaaS setup.</p>
          </div>
          <div className="steps">
            <div className="step">
              <div className="num">01</div>
              <h3>Configure</h3>
              <p>Import your WBS, cost codes and org chart. Turn on the modules you need.</p>
            </div>
            <div className="step">
              <div className="num">02</div>
              <h3>Migrate</h3>
              <p>P6, MS Project, SAP and Excel data lands in Bipros — mapped, not manually re-entered.</p>
            </div>
            <div className="step">
              <div className="num">03</div>
              <h3>Roll out</h3>
              <p>Role-based training for PMs, planners, cost controllers and site engineers.</p>
            </div>
            <div className="step">
              <div className="num">04</div>
              <h3>Operate</h3>
              <p>Weekly Board pack auto-generated. Field crews report from day one on mobile.</p>
            </div>
          </div>
        </div>
      </section>

      {/* ───────── CTA BAND ───────── */}
      <section className="cta-band">
        <div className="container">
          <h2>Ready to run your portfolio from one place?</h2>
          <p>
            A 30-minute working session with our solutions team. Bring your program; we&rsquo;ll show
            you what it looks like inside Bipros.
          </p>
          <div className="row">
            <a href="#signin" className="btn btn-primary btn-lg">Request a demo →</a>
            <a
              href="#"
              className="btn btn-ghost btn-lg"
              style={{ color: "#fff", borderColor: "rgba(255,255,255,0.22)" }}
              onClick={(e) => e.preventDefault()}
            >
              Download capability deck
            </a>
          </div>
        </div>
      </section>

      {/* ───────── FOOTER ───────── */}
      <footer>
        <div className="container">
          <div className="ft-grid">
            <div className="ft-brand">
              <a href="#" className="brand" aria-label="Bipros EPPM home">
                <span className="brand-mark">
                  <img src="/bipros-logo.png" alt="" />
                </span>
                <span style={{ color: "#fff" }}>
                  Bipros<span className="e">EPPM</span>
                </span>
              </a>
              <p>Enterprise Project Portfolio Management for infrastructure, energy and field-heavy programs.</p>
              <p style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: 12, color: "#5A6378" }}>
                © 2026 Bipros Technologies Pvt. Ltd.
              </p>
            </div>
            <div className="ft-col">
              <h4>Product</h4>
              <a href="#" onClick={(e) => e.preventDefault()}>Platform overview</a>
              <a href="#" onClick={(e) => e.preventDefault()}>Modules</a>
              <a href="#" onClick={(e) => e.preventDefault()}>Integrations</a>
              <a href="#" onClick={(e) => e.preventDefault()}>Mobile app</a>
              <a href="#" onClick={(e) => e.preventDefault()}>Security</a>
            </div>
            <div className="ft-col">
              <h4>Industries</h4>
              <a href="#" onClick={(e) => e.preventDefault()}>Road &amp; Transport</a>
              <a href="#" onClick={(e) => e.preventDefault()}>Energy &amp; Utilities</a>
              <a href="#" onClick={(e) => e.preventDefault()}>Water</a>
              <a href="#" onClick={(e) => e.preventDefault()}>Rail &amp; Metro</a>
              <a href="#" onClick={(e) => e.preventDefault()}>Government</a>
            </div>
            <div className="ft-col">
              <h4>Resources</h4>
              <a href="#" onClick={(e) => e.preventDefault()}>Documentation</a>
              <a href="#" onClick={(e) => e.preventDefault()}>Customer stories</a>
              <a href="#" onClick={(e) => e.preventDefault()}>Webinars</a>
              <a href="#" onClick={(e) => e.preventDefault()}>API reference</a>
              <a href="#" onClick={(e) => e.preventDefault()}>Status</a>
            </div>
            <div className="ft-col">
              <h4>Company</h4>
              <a href="#" onClick={(e) => e.preventDefault()}>About</a>
              <a href="#" onClick={(e) => e.preventDefault()}>Careers</a>
              <a href="#" onClick={(e) => e.preventDefault()}>Partners</a>
              <a href="#" onClick={(e) => e.preventDefault()}>Contact</a>
              <a href="#" onClick={(e) => e.preventDefault()}>Press kit</a>
            </div>
          </div>
          <div className="ft-bottom">
            <div>Privacy · Terms · DPA · Cookies</div>
            <div className="socials">
              <a className="soc" href="#" aria-label="LinkedIn" onClick={(e) => e.preventDefault()}>in</a>
              <a className="soc" href="#" aria-label="X" onClick={(e) => e.preventDefault()}>X</a>
              <a className="soc" href="#" aria-label="YouTube" onClick={(e) => e.preventDefault()}>▶</a>
              <a className="soc" href="#" aria-label="GitHub" onClick={(e) => e.preventDefault()}>❮❯</a>
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
}
