import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { authAPI } from "@ebike/shared-code/api";
import { useAuth } from "@ebike/shared-code/hooks";
import type { UserProfileResponse } from "@ebike/shared-code/types";
import SectionShell from "../../components/common/SectionShell";

/* ─── tiny helpers ─────────────────────────────────────────────── */
const initials = (name: string) =>
  name
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0].toUpperCase())
    .join("");

/* ─── sub-components ────────────────────────────────────────────── */

function StatusDot({ active }: { active: boolean }) {
  return (
    <span
      style={{
        display: "inline-block",
        width: 8,
        height: 8,
        borderRadius: "50%",
        background: active ? "#22c55e" : "#f59e0b",
        boxShadow: active ? "0 0 0 3px rgba(34,197,94,.22)" : "0 0 0 3px rgba(245,158,11,.22)",
        flexShrink: 0,
      }}
    />
  );
}

function Tag({
  children,
  variant = "default",
}: {
  children: React.ReactNode;
  variant?: "default" | "sky" | "green" | "amber";
}) {
  const colors: Record<string, { bg: string; color: string; border: string }> = {
    default: { bg: "rgba(255,255,255,.07)", color: "rgba(255,255,255,.65)", border: "rgba(255,255,255,.12)" },
    sky: { bg: "rgba(14,165,233,.15)", color: "#7dd3fc", border: "rgba(14,165,233,.3)" },
    green: { bg: "rgba(34,197,94,.12)", color: "#86efac", border: "rgba(34,197,94,.25)" },
    amber: { bg: "rgba(245,158,11,.12)", color: "#fcd34d", border: "rgba(245,158,11,.25)" },
  };
  const c = colors[variant];
  return (
    <span
      style={{
        display: "inline-flex",
        alignItems: "center",
        gap: 6,
        padding: "4px 12px",
        borderRadius: 999,
        fontSize: 11,
        fontWeight: 600,
        letterSpacing: "0.1em",
        textTransform: "uppercase",
        background: c.bg,
        color: c.color,
        border: `1px solid ${c.border}`,
        fontFamily: "'DM Mono', monospace",
      }}
    >
      {children}
    </span>
  );
}

function PermissionChip({ label }: { label: string }) {
  return (
    <span
      style={{
        display: "inline-block",
        padding: "5px 12px",
        background: "rgba(255,255,255,.04)",
        border: "1px solid rgba(255,255,255,.1)",
        borderRadius: 8,
        fontSize: 11,
        fontFamily: "'DM Mono', monospace",
        color: "rgba(255,255,255,.55)",
        letterSpacing: "0.06em",
      }}
    >
      {label}
    </span>
  );
}

function SkeletonLine({ w = "100%", h = 14 }: { w?: string; h?: number }) {
  return (
    <div
      style={{
        width: w,
        height: h,
        borderRadius: 6,
        background: "rgba(255,255,255,.07)",
        animation: "pulse 1.4s ease-in-out infinite",
      }}
    />
  );
}

/* ─── main component ────────────────────────────────────────────── */

const CustomerProfilePageApi = () => {
  const navigate = useNavigate();
  const { isAuthenticated, isBootstrapping, user, logout } = useAuth();
  const [profile, setProfile] = useState<UserProfileResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");

  const profileLabel = useMemo(() => {
    if (!profile) return "";
    const fullName = [profile.firstName, profile.lastName].filter(Boolean).join(" ").trim();
    return fullName || user?.fullName || profile.username;
  }, [profile, user?.fullName]);

  useEffect(() => {
    if (!isBootstrapping && !isAuthenticated) navigate("/auth", { replace: true });
  }, [isAuthenticated, isBootstrapping, navigate]);

  useEffect(() => {
    let isMounted = true;
    const loadProfile = async () => {
      if (isBootstrapping) {
        return;
      }
      if (!isAuthenticated) {
        if (isMounted) { setError("B???n ch??a ????ng nh???p."); setIsLoading(false); }
        return;
      }
      setIsLoading(true); setError("");
      try {
        const response = await authAPI.getProfile();
        if (isMounted) setProfile(response);
      } catch (err) {
        if (isMounted)
          setError(err instanceof Error ? err.message : "Kh??ng th??? t???i h??? s?? l??c n??y.");
      } finally {
        if (isMounted) setIsLoading(false);
      }
    };
    void loadProfile();
    return () => { isMounted = false; };
  }, [isAuthenticated, isBootstrapping]);
  const label = profileLabel || "Tài khoản";
  const mono = "'DM Mono', 'Fira Mono', monospace";
  const serif = "'Playfair Display', Georgia, serif";

  return (
    <SectionShell
      eyebrow="Hồ Sơ Khách Hàng"
      title="Thông tin tài khoản"
      description=""
    >
      {/* ── font import ── */}
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=DM+Mono:wght@400;500&family=Playfair+Display:wght@700;900&display=swap');
        @keyframes pulse { 0%,100%{opacity:1} 50%{opacity:.4} }
        @keyframes fadeUp { from{opacity:0;transform:translateY(14px)} to{opacity:1;transform:none} }
        .profile-card { animation: fadeUp .45s ease both; }
      `}</style>

      <div style={{ padding: "0 24px 64px" }}>
        {/* ── outer shell ── */}
        <div
          className="profile-card"
          style={{
            background: "#0c0f12",
            borderRadius: 28,
            border: "1px solid rgba(255,255,255,.09)",
            overflow: "hidden",
            position: "relative",
          }}
        >
          {/* ambient glow top-left */}
          <div
            style={{
              position: "absolute",
              inset: 0,
              background:
                "radial-gradient(ellipse 56% 40% at 8% 0%, rgba(99,102,241,.18) 0%, transparent 70%), " +
                "radial-gradient(ellipse 40% 30% at 90% 0%, rgba(20,184,166,.14) 0%, transparent 65%)",
              pointerEvents: "none",
            }}
          />

          {/* ── top bar ── */}
          <div
            style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
              padding: "18px 28px",
              borderBottom: "1px solid rgba(255,255,255,.06)",
              position: "relative",
            }}
          >
            <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
              <div
                style={{
                  width: 8,
                  height: 8,
                  borderRadius: "50%",
                  background: "#ef4444",
                  boxShadow: "14px 0 0 #f59e0b, 28px 0 0 #22c55e",
                }}
              />
            </div>

            <span
              style={{
                fontFamily: mono,
                fontSize: 11,
                color: "rgba(255,255,255,.3)",
                letterSpacing: "0.12em",
                textTransform: "uppercase",
              }}
            >
              ebike / profile
            </span>

            <button
              type="button"
              onClick={() => { logout(); setProfile(null); }}
              style={{
                fontFamily: mono,
                fontSize: 11,
                letterSpacing: "0.08em",
                textTransform: "uppercase",
                color: "rgba(255,255,255,.4)",
                background: "transparent",
                border: "1px solid rgba(255,255,255,.12)",
                borderRadius: 8,
                padding: "5px 14px",
                cursor: "pointer",
                transition: "color .2s, border-color .2s",
              }}
              onMouseEnter={(e) => {
                (e.target as HTMLButtonElement).style.color = "#f87171";
                (e.target as HTMLButtonElement).style.borderColor = "rgba(239,68,68,.4)";
              }}
              onMouseLeave={(e) => {
                (e.target as HTMLButtonElement).style.color = "rgba(255,255,255,.4)";
                (e.target as HTMLButtonElement).style.borderColor = "rgba(255,255,255,.12)";
              }}
            >
              Đăng xuất
            </button>
          </div>

          {/* ── main content ── */}
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "1fr 340px",
              gap: 0,
              position: "relative",
            }}
          >
            {/* ── LEFT COLUMN ── */}
            <div style={{ padding: "36px 36px 40px", borderRight: "1px solid rgba(255,255,255,.06)" }}>
              {/* hero row */}
              <div style={{ display: "flex", alignItems: "flex-start", gap: 20, marginBottom: 32 }}>
                {/* avatar */}
                <div
                  style={{
                    width: 72,
                    height: 72,
                    borderRadius: 20,
                    background: "linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%)",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    fontFamily: serif,
                    fontSize: 28,
                    fontWeight: 700,
                    color: "#fff",
                    flexShrink: 0,
                    letterSpacing: "-0.01em",
                  }}
                >
                  {initials(label)}
                </div>

                <div style={{ flex: 1, minWidth: 0 }}>
                  <p
                    style={{
                      margin: "0 0 4px",
                      fontFamily: mono,
                      fontSize: 10,
                      letterSpacing: "0.18em",
                      textTransform: "uppercase",
                      color: "#818cf8",
                    }}
                  >
                    Người dùng
                  </p>
                  <h2
                    style={{
                      margin: "0 0 12px",
                      fontFamily: serif,
                      fontSize: "clamp(24px, 3vw, 36px)",
                      fontWeight: 900,
                      color: "#fff",
                      lineHeight: 1.1,
                      letterSpacing: "-0.02em",
                    }}
                  >
                    {label}
                  </h2>
                  <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
                    <Tag variant={profile?.active ? "green" : "amber"}>
                      <StatusDot active={!!profile?.active} />
                      {profile?.active ? "Đang hoạt động" : "Tạm khóa"}
                    </Tag>
                    <Tag variant={profile?.verified ? "sky" : "default"}>
                      {profile?.verified ? "✓ Đã xác minh" : "Chưa xác minh"}
                    </Tag>
                  </div>
                </div>
              </div>

              {/* ── info / error / loading ── */}
              {isLoading ? (
                <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
                  <SkeletonLine w="60%" />
                  <SkeletonLine />
                  <SkeletonLine w="80%" />
                </div>
              ) : error ? (
                <div
                  style={{
                    background: "rgba(239,68,68,.08)",
                    border: "1px solid rgba(239,68,68,.25)",
                    borderRadius: 16,
                    padding: "16px 20px",
                    color: "#fca5a5",
                    fontFamily: mono,
                    fontSize: 13,
                    lineHeight: 1.7,
                  }}
                >
                  ✕ {error}
                </div>
              ) : (
                <>
                  {/* info rows */}
                  {[
                    { label: "Email", value: profile?.email },
                    { label: "Username", value: `@${profile?.username}` },
                    { label: "User ID", value: `#${profile?.userId}` },
                  ].map(({ label: lbl, value }) => (
                    <div
                      key={lbl}
                      style={{
                        display: "grid",
                        gridTemplateColumns: "100px 1fr",
                        alignItems: "center",
                        gap: 12,
                        padding: "12px 0",
                        borderBottom: "1px solid rgba(255,255,255,.05)",
                      }}
                    >
                      <span
                        style={{
                          fontFamily: mono,
                          fontSize: 10,
                          letterSpacing: "0.14em",
                          textTransform: "uppercase",
                          color: "rgba(255,255,255,.3)",
                        }}
                      >
                        {lbl}
                      </span>
                      <span
                        style={{
                          fontFamily: mono,
                          fontSize: 13,
                          color: "rgba(255,255,255,.8)",
                        }}
                      >
                        {value}
                      </span>
                    </div>
                  ))}

                  {/* roles section */}
                  <div style={{ marginTop: 28 }}>
                    <p
                      style={{
                        fontFamily: mono,
                        fontSize: 10,
                        letterSpacing: "0.14em",
                        textTransform: "uppercase",
                        color: "rgba(255,255,255,.3)",
                        marginBottom: 12,
                      }}
                    >
                      Vai trò
                    </p>
                    <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
                      {(profile?.roles ?? []).map((role) => (
                        <Tag key={role} variant="sky">
                          {role}
                        </Tag>
                      ))}
                      {(profile?.roles.length ?? 0) === 0 && (
                        <span style={{ fontFamily: mono, fontSize: 12, color: "rgba(255,255,255,.25)" }}>
                          Chưa có vai trò
                        </span>
                      )}
                    </div>
                  </div>
                </>
              )}
            </div>

            {/* ── RIGHT COLUMN ── */}
            <div style={{ padding: "36px 28px 40px", display: "flex", flexDirection: "column", gap: 24 }}>
              {/* stat card */}
              <div
                style={{
                  background: "rgba(255,255,255,.03)",
                  border: "1px solid rgba(255,255,255,.07)",
                  borderRadius: 18,
                  padding: "20px 22px",
                }}
              >
                <p
                  style={{
                    fontFamily: mono,
                    fontSize: 10,
                    letterSpacing: "0.16em",
                    textTransform: "uppercase",
                    color: "rgba(255,255,255,.3)",
                    marginBottom: 16,
                  }}
                >
                  Tổng quan
                </p>
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
                  {[
                    { label: "Vai trò", value: profile?.roles.length ?? "—" },
                    { label: "Permissions", value: profile?.permissions.length ?? "—" },
                  ].map(({ label: lbl, value }) => (
                    <div
                      key={lbl}
                      style={{
                        background: "rgba(255,255,255,.04)",
                        borderRadius: 12,
                        padding: "14px 16px",
                      }}
                    >
                      <p
                        style={{
                          fontFamily: mono,
                          fontSize: 9,
                          letterSpacing: "0.14em",
                          textTransform: "uppercase",
                          color: "rgba(255,255,255,.3)",
                          margin: "0 0 6px",
                        }}
                      >
                        {lbl}
                      </p>
                      <p
                        style={{
                          fontFamily: serif,
                          fontSize: 32,
                          fontWeight: 900,
                          color: "#fff",
                          margin: 0,
                          lineHeight: 1,
                        }}
                      >
                        {value}
                      </p>
                    </div>
                  ))}
                </div>
              </div>

              {/* permissions */}
              <div
                style={{
                  background: "rgba(255,255,255,.03)",
                  border: "1px solid rgba(255,255,255,.07)",
                  borderRadius: 18,
                  padding: "20px 22px",
                  flex: 1,
                }}
              >
                <p
                  style={{
                    fontFamily: mono,
                    fontSize: 10,
                    letterSpacing: "0.16em",
                    textTransform: "uppercase",
                    color: "rgba(255,255,255,.3)",
                    marginBottom: 14,
                  }}
                >
                  Permissions
                </p>
                {isLoading ? (
                  <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                    <SkeletonLine /><SkeletonLine w="70%" /><SkeletonLine w="85%" />
                  </div>
                ) : (
                  <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
                    {(profile?.permissions ?? []).map((p) => (
                      <PermissionChip key={p} label={p} />
                    ))}
                    {(profile?.permissions.length ?? 0) === 0 && (
                      <span style={{ fontFamily: mono, fontSize: 12, color: "rgba(255,255,255,.22)" }}>
                        Không có permission nào.
                      </span>
                    )}
                  </div>
                )}
              </div>

              {/* footnote */}
              <p
                style={{
                  fontFamily: mono,
                  fontSize: 10,
                  letterSpacing: "0.1em",
                  color: "rgba(255,255,255,.2)",
                  lineHeight: 1.8,
                  margin: 0,
                  borderTop: "1px solid rgba(255,255,255,.06)",
                  paddingTop: 16,
                }}
              >
                Dữ liệu được đọc trực tiếp từ JWT và endpoint hồ sơ backend Spring.
              </p>
            </div>
          </div>
        </div>
      </div>
    </SectionShell>
  );
};

export default CustomerProfilePageApi;

