# QA work queue (one small step per 30-minute tick)

Rules for every tick:
- First: `curl -s localhost:8088/api/v1/health` (app) and `curl -s 127.0.0.1:2526/mails` (SMTP sink). If down, restart as in the session (env vars only, never write secrets to files).
- At most ONE background agent running at a time. If an agent finished since last tick: review its report (verify each claimed bug in code, drop false ones), cherry-pick its commit onto claude/determined-moser-d00a1b, note results in findings.md, mark its row [x].
- If no agent is running: resume the next [ ] agent row with SendMessage ("continue where you stopped; keep STEP 0 base 6b09bdf; finish, commit, report").
- Also do the next [ ] manual row (small). Keep tool output short.
- End of every tick: copy findings.md and queue.md to D:\WWW\WWW_EBikeStore\.claude\worktrees\determined-moser-d00a1b\QA\ (durable copy; scratchpad can be wiped).
- Pace chosen by the user (2026-11-26): keep 30-min ticks, one agent at a time.

## Agents (resume in this order)
- [x] booking — committed fdcfe9e (768 tests, 1 low bug)
- [x] order v2 — cherry-picked (635 tests, 5 bugs; U-01 critical live-confirmed)
- [x] backoffice v2 — cherry-picked (589 tests, 17 bugs; U-06, U-07 live-confirmed)
- [x] catalog v2 — cherry-picked (398 tests, 22 bugs; U-16 live-confirmed)
- [x] ops — cherry-picked (287 tests, 3 low bugs)
- [x] selenium — cherry-picked d3bb2d3 (180 tests, 8 bug tests; S-01..S-03 new)

## Manual rows
- [x] storefront, catalogue filters, detail, cart, guest checkout, signup/login, guest-order link, profile, addresses, favourites, cancel, manager order lifecycle, payments/VNPay (probe pay)
- [x] probe admin: `node probe.mjs admin` (REGISTRATION_SERVICE restored to 500000)
- [x] probe stock: `node probe.mjs stock`
- [x] probe svc: `node probe.mjs svc`
- [x] Chrome: admin UI pages — accounts, overview, pricing, audit log, catalog load without errors; XSS name shown as text; pages refuse iframe embedding
- [x] 375px (built-in browser mobile preset): no page-level horizontal scroll on home, products, detail, checkout, login, support; home hero still an empty box on mobile
- [x] UI audit (ui-ux-pro-max checklist, JS audit on /, /products, detail, checkout, login)
- [x] Rate limiter: 6th login/min → 429 Retry-After 59; XFF does not bypass; verify OTP has no limit (tested on a 2nd copy at :8089)
- NOTE for final report: SMTP = local fake sink (127.0.0.1:2525), VNPay = fake TMN/secret, IPN/return simulated by self-signing. Real SMTP delivery and real VNPay sandbox payment NOT tested (user has no credentials yet).
- [x] Final: unit 2677 (69 bug red), Selenium 180 (8 bug red, 1 flaky passes alone); report QA/BAO_CAO_KIEM_THU.md committed; cron deleted
