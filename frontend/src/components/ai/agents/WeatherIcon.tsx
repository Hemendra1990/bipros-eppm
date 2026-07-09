"use client";

import styles from "./WeatherIcon.module.css";

type Kind =
  | "sun"
  | "partly"
  | "cloud"
  | "fog"
  | "drizzle"
  | "rain"
  | "heavyRain"
  | "showers"
  | "snow"
  | "sleet"
  | "storm";

/** WMO weather-interpretation code → animation kind (mirrors OpenMeteoClient.describeCode bands). */
function kindForCode(code: number | null | undefined): Kind {
  if (code == null) return "cloud";
  switch (code) {
    case 0:
      return "sun";
    case 1:
    case 2:
      return "partly";
    case 3:
      return "cloud";
    case 45:
    case 48:
      return "fog";
    case 51:
    case 53:
    case 55:
      return "drizzle";
    case 61:
    case 63:
      return "rain";
    case 65:
    case 82:
      return "heavyRain";
    case 66:
    case 67:
      return "sleet";
    case 71:
    case 73:
    case 75:
    case 77:
    case 85:
    case 86:
      return "snow";
    case 80:
    case 81:
      return "showers";
    case 95:
    case 96:
    case 99:
      return "storm";
    default:
      return "cloud";
  }
}

function Sun() {
  return (
    <>
      <div className={styles.rays}>
        {Array.from({ length: 8 }).map((_, i) => (
          <span key={i} className={styles.ray} style={{ transform: `rotate(${i * 45}deg)` }} />
        ))}
      </div>
      <div className={styles.sun} />
    </>
  );
}

function Drops({ count, cls }: { count: number; cls?: string }) {
  return (
    <>
      {Array.from({ length: count }).map((_, i) => (
        <span
          key={i}
          className={`${styles.drop} ${cls ?? ""}`}
          style={{ left: `${22 + i * (56 / Math.max(1, count - 1))}%`, animationDelay: `${i * 0.22}s` }}
        />
      ))}
    </>
  );
}

function Flakes({ count }: { count: number }) {
  return (
    <>
      {Array.from({ length: count }).map((_, i) => (
        <span
          key={i}
          className={styles.flake}
          style={{ left: `${24 + i * (48 / Math.max(1, count - 1))}%`, animationDelay: `${i * 0.5}s` }}
        />
      ))}
    </>
  );
}

/**
 * Animated weather glyph for a forecast day. Pure CSS keyframes, no assets or libraries. Pass the
 * WMO {@code code}; unknown/undefined falls back to a drifting cloud. Respects reduced-motion.
 */
export function WeatherIcon({
  code,
  size = 34,
  title,
}: {
  code: number | null | undefined;
  size?: number;
  title?: string;
}) {
  const kind = kindForCode(code);
  const box = { width: size, height: size, fontSize: size } as const;

  return (
    <div className={styles.icon} style={box} role="img" aria-label={title ?? kind} title={title}>
      {kind === "sun" && <Sun />}

      {kind === "partly" && (
        <>
          <div className={styles.sunCorner} />
          <div className={styles.cloud} />
        </>
      )}

      {kind === "cloud" && <div className={styles.cloud} />}

      {kind === "fog" && (
        <>
          <div className={styles.fogLine} style={{ top: "34%" }} />
          <div className={styles.fogLine} style={{ top: "50%", animationDelay: "0.6s" }} />
          <div className={styles.fogLine} style={{ top: "66%", animationDelay: "1.2s" }} />
        </>
      )}

      {kind === "drizzle" && (
        <>
          <div className={styles.cloud} />
          <Drops count={3} cls={styles.dropLight} />
        </>
      )}

      {kind === "rain" && (
        <>
          <div className={`${styles.cloud} ${styles.cloudDark}`} />
          <Drops count={3} />
        </>
      )}

      {kind === "heavyRain" && (
        <>
          <div className={`${styles.cloud} ${styles.cloudDark}`} />
          <Drops count={5} cls={styles.dropFast} />
        </>
      )}

      {kind === "showers" && (
        <>
          <div className={styles.sunCorner} />
          <div className={styles.cloud} />
          <Drops count={3} />
        </>
      )}

      {kind === "sleet" && (
        <>
          <div className={`${styles.cloud} ${styles.cloudDark}`} />
          <Drops count={2} />
          <Flakes count={2} />
        </>
      )}

      {kind === "snow" && (
        <>
          <div className={styles.cloud} />
          <Flakes count={4} />
        </>
      )}

      {kind === "storm" && (
        <>
          <div className={`${styles.cloud} ${styles.cloudDark}`} />
          <span className={styles.bolt} />
          <Drops count={2} cls={styles.dropFast} />
        </>
      )}
    </div>
  );
}
