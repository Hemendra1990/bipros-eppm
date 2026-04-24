"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import toast from "react-hot-toast";
import { Eye, EyeOff } from "lucide-react";
import { authApi } from "@/lib/api/authApi";
import { useAuthStore } from "@/lib/state/store";

export function LandingHero() {
  const router = useRouter();
  const { setAuth } = useAuthStore();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [remember, setRemember] = useState(true);
  const [showPw, setShowPw] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const ssoNotAvailable = () => toast("SSO sign-in is not yet configured.", { icon: "🔒" });

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);

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
      setSubmitting(false);
    }
  };

  return (
    <section
      className="relative grid gap-12 px-9 py-20 lg:grid-cols-[1.25fr_1fr]"
      style={{ background: "linear-gradient(180deg,#FFFFFF 0%, #FAF9F6 100%)" }}
    >
      <div
        aria-hidden
        className="pointer-events-none absolute right-[-120px] top-[-120px] h-[340px] w-[340px] rounded-full"
        style={{ background: "radial-gradient(circle, rgba(212,175,55,0.08) 0%, transparent 70%)" }}
      />
      <div className="relative">
        <div className="mb-3.5 flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.18em] text-gold-deep">
          <span aria-hidden className="inline-block h-px w-5 bg-gold" />
          Enterprise PPM
        </div>
        <h1
          className="font-display font-semibold text-[56px] leading-[1.04] tracking-[-0.018em] text-charcoal"
          style={{ fontVariationSettings: "'opsz' 144" }}
        >
          Run every infrastructure programme
          <br />
          as <em className="not-italic font-medium italic text-gold-deep">one</em>.
        </h1>
        <p className="mt-4 max-w-[500px] text-[17px] leading-relaxed text-charcoal">
          Portfolio, schedule, cost, risk, and field operations on a single spine. Trusted by
          programme leaders to deliver at scale — from rail corridors to solar farms.
        </p>
        <div className="mt-7 flex gap-2.5">
          <button
            type="button"
            className="inline-flex h-12 items-center gap-1.5 rounded-xl bg-gold px-5 text-sm font-semibold text-paper transition-all duration-200 hover:bg-gold-deep hover:shadow-[0_4px_14px_rgba(212,175,55,0.3)] hover:-translate-y-px"
          >
            Request demo →
          </button>
          <button
            type="button"
            className="inline-flex h-12 items-center rounded-xl border border-gold bg-paper px-5 text-sm font-semibold text-gold-deep hover:bg-ivory"
          >
            Explore the platform
          </button>
        </div>
        <div className="mt-8 flex items-center gap-4">
          <div className="flex">
            {["MC", "JR", "AS", "+"].map((s, i) => (
              <div
                key={i}
                className={`flex h-8 w-8 items-center justify-center rounded-full bg-parchment border-2 border-paper ${i ? "-ml-2" : ""} font-display font-semibold text-[11px] text-gold-deep`}
              >
                {s}
              </div>
            ))}
          </div>
          <div>
            <div className="text-[12px] text-gold-deep">★★★★★ <span className="font-medium text-slate">4.8 on G2</span></div>
            <div className="text-[12px] text-slate">
              <strong className="text-charcoal">400+ teams</strong> delivering programmes on Bipros
            </div>
          </div>
        </div>
      </div>

      {/* Sign-in card */}
      <form
        onSubmit={handleSubmit}
        className="relative z-10 rounded-2xl border border-hairline bg-paper p-7 shadow-[0_4px_20px_rgba(28,28,28,0.05)]"
      >
        <h3 className="font-display text-xl font-semibold text-charcoal">Welcome back</h3>
        <div className="mt-0.5 text-xs text-slate">Sign in to your portfolio</div>

        <div className="mt-4 grid grid-cols-3 gap-1.5">
          {["Google", "Microsoft", "SSO"].map((p) => (
            <button
              key={p}
              type="button"
              onClick={ssoNotAvailable}
              className="h-10 rounded-[10px] border border-hairline bg-paper text-[11px] font-medium text-charcoal hover:border-gold transition-colors"
            >
              {p}
            </button>
          ))}
        </div>

        <div className="my-4 flex items-center gap-3 text-[10px] font-semibold uppercase tracking-[0.14em] text-ash">
          <div className="h-px flex-1 bg-hairline" />
          or with email
          <div className="h-px flex-1 bg-hairline" />
        </div>

        {error && (
          <div className="mb-3 rounded-md border border-burgundy/30 bg-burgundy/10 px-3 py-2 text-xs text-burgundy">
            {error}
          </div>
        )}

        <label className="block text-xs font-semibold text-charcoal mb-1.5">Email or username</label>
        <input
          type="text"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          placeholder="you@company.com"
          required
          autoComplete="username"
          className="mb-3 h-10 w-full rounded-[10px] border border-divider bg-paper px-3.5 text-sm text-charcoal placeholder:text-ash outline-none transition-all duration-[120ms] hover:border-gold-deep/50 focus:border-gold focus:shadow-[0_0_0_3px_rgba(212,175,55,0.18)]"
        />

        <label className="block text-xs font-semibold text-charcoal mb-1.5">Password</label>
        <div className="relative mb-3">
          <input
            type={showPw ? "text" : "password"}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            autoComplete="current-password"
            className="h-10 w-full rounded-[10px] border border-divider bg-paper px-3.5 pr-10 text-sm text-charcoal outline-none transition-all duration-[120ms] hover:border-gold-deep/50 focus:border-gold focus:shadow-[0_0_0_3px_rgba(212,175,55,0.18)]"
          />
          <button
            type="button"
            onClick={() => setShowPw((s) => !s)}
            aria-label={showPw ? "Hide password" : "Show password"}
            className="absolute right-2.5 top-1/2 -translate-y-1/2 rounded-md p-1 text-slate hover:text-gold-deep"
          >
            {showPw ? <EyeOff size={15} /> : <Eye size={15} />}
          </button>
        </div>

        <div className="mb-4 flex items-center justify-between text-[11px] text-slate">
          <label className="flex items-center gap-1.5">
            <input
              type="checkbox"
              checked={remember}
              onChange={(e) => setRemember(e.target.checked)}
              className="accent-[#D4AF37]"
            />
            Remember me
          </label>
          <a className="font-semibold text-gold-deep hover:text-gold-ink cursor-pointer">Forgot?</a>
        </div>

        <button
          type="submit"
          disabled={submitting}
          className="inline-flex h-12 w-full items-center justify-center rounded-xl bg-gold text-sm font-semibold text-paper transition-all duration-200 hover:bg-gold-deep hover:shadow-[0_4px_14px_rgba(212,175,55,0.3)] hover:-translate-y-px disabled:opacity-70 disabled:cursor-not-allowed disabled:translate-y-0 disabled:shadow-none"
        >
          {submitting ? "Signing in…" : "Sign in"}
        </button>

        <div className="mt-4 flex justify-center gap-1.5">
          {["SOC 2", "ISO 27001", "SSO / SAML"].map((t) => (
            <span
              key={t}
              className="rounded border border-hairline bg-ivory px-2 py-1 font-mono text-[9px] uppercase tracking-[0.1em] text-slate"
            >
              {t}
            </span>
          ))}
        </div>
      </form>
    </section>
  );
}
