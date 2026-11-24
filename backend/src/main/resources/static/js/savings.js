// V33: "how much does an electric bike save" calculator on the bike page. The rider types the kilometres a day and the days a month; the server compares the
// cost of charging this bike with the petrol of a motorbike of the same class, using the prices the admin set. Open to everyone, visitors included.
(() => {
  'use strict';
  const page = document.querySelector('#productPage');
  const box = document.querySelector('#savingsBox');
  if (!page || !box || !page.dataset.slug) return;
  const body = box.querySelector('[data-body]');
  const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));
  const localeTag = () => document.documentElement.lang === 'en' ? 'en-GB' : 'vi-VN';
  // money and plain numbers follow the page language together, so one sentence never mixes "2.900 ₫" (Vietnamese grouping) with "1.06 kWh" (English decimals)
  const money = value => new Intl.NumberFormat(localeTag(), {maximumFractionDigits: 0}).format(Number(value) || 0) + ' ₫';
  // prices and consumption come from DECIMAL columns ("1.80"): shown as 1.8 (1,8 in Vietnamese), never with trailing zeros
  const plain = value => new Intl.NumberFormat(localeTag(), {maximumFractionDigits: 2}).format(Number(value) || 0);
  const DEFAULT_KM = 20, DEFAULT_DAYS = 26;
  let limits = {maxKmPerDay: 300, maxDaysPerMonth: 30};
  let sequence = 0, timer = null;

  async function request(km, days) {
    let response;
    try {
      response = await fetch(`/api/v1/products/${encodeURIComponent(page.dataset.slug)}/savings?kmPerDay=${km}&daysPerMonth=${days}`, {
        credentials: 'same-origin', headers: {Accept: 'application/json'}
      });
    } catch (offline) {
      // no network: the browser's own message ("Failed to fetch") is English whatever the page language is
      throw Error('Không thực hiện được. Vui lòng thử lại sau.');
    }
    if (!response.ok) {
      let message = '';
      try { message = (await response.json()).message || ''; } catch (ignored) { /* not JSON */ }
      const error = Error(message || 'Không thực hiện được. Vui lòng thử lại sau.');
      error.status = response.status;
      throw error;
    }
    return response.json();
  }

  // 404: the bike is hidden or removed; 401/403: the account has no right to view products (a few staff roles), so a retry would only fail again
  const isGone = error => [401, 403, 404].includes(error.status);
  const field = name => body.querySelector(`[name="${name}"]`);
  const wholeNumber = (input, max) => {
    const text = String(input.value).trim();
    if (!/^\d{1,4}$/.test(text)) return null;
    const value = Number(text);
    return value >= 1 && value <= max ? value : null;
  };
  const paintProblem = text => { const slot = body.querySelector('[data-result]'); if (slot) slot.innerHTML = `<p class="installment-problem" role="alert">${esc(text)}</p>`; };

  function paintResult(r) {
    const slot = body.querySelector('[data-result]');
    if (!slot) return;
    const head = r.saves
      ? `<div class="installment-main"><dt>${'Mỗi tháng bạn tiết kiệm khoảng'}</dt><dd data-field="monthly-saving">${esc(money(r.monthlySaving))}</dd></div>
         <div><dt>${'Mỗi năm tiết kiệm khoảng'}</dt><dd data-field="yearly-saving">${esc(money(r.yearlySaving))}</dd></div>`
      : `<div class="installment-main"><dt>${'Kết quả'}</dt><dd data-field="no-saving">${'Với giá hiện tại, xe này không rẻ hơn xe xăng cùng phân khúc.'}</dd></div>`;
    slot.innerHTML = `<dl class="installment-figures savings-figures">${head}
        <div><dt>${'Tiền sạc mỗi tháng'}</dt><dd data-field="electric">${esc(money(r.monthlyElectricityCost))}</dd></div>
        <div><dt>${'Tiền xăng mỗi tháng'}</dt><dd data-field="petrol">${esc(money(r.monthlyPetrolCost))}</dd></div>
        <div><dt>${'Sạc 100 km'}</dt><dd>${esc(money(r.electricityCostPer100Km))}</dd></div>
        <div><dt>${'Xăng 100 km'}</dt><dd>${esc(money(r.petrolCostPer100Km))}</dd></div></dl>
      <p class="muted" data-assumptions>${esc(`Ước tính theo ${plain(r.monthlyKm)} km mỗi tháng. Pin khoảng ${plain(r.batteryKwh)} kWh, hao ${plain(r.kwhPer100Km)} kWh cho 100 km (đã tính quãng đường thực tế bằng ${r.realRangePercent}% công bố và hiệu suất sạc ${r.chargerEfficiencyPercent}%). Giá điện ${money(r.electricityPricePerKwh)} mỗi kWh; xe máy xăng cùng phân khúc tiêu hao ${plain(r.petrolLitersPer100Km)} lít/100 km, giá xăng ${money(r.petrolPricePerLiter)} mỗi lít. Chỉ mang tính tham khảo, không cộng vào đơn hàng.`)}</p>`;
  }

  async function refresh() {
    const mine = ++sequence;
    const km = wholeNumber(field('kmPerDay'), limits.maxKmPerDay), days = wholeNumber(field('daysPerMonth'), limits.maxDaysPerMonth);
    if (km === null) { paintProblem(`Nhập số km mỗi ngày là số nguyên từ 1 đến ${limits.maxKmPerDay}.`); return; }
    if (days === null) { paintProblem(`Nhập số ngày đi mỗi tháng là số nguyên từ 1 đến ${limits.maxDaysPerMonth}.`); return; }
    try {
      const answer = await request(km, days);
      if (mine !== sequence) return; // an older answer arriving late
      if (!answer.calculable) { showUnavailable(answer); return; }
      paintResult(answer);
    } catch (error) {
      if (mine !== sequence) return;
      if (isGone(error)) { box.hidden = true; box.dataset.state = 'hidden'; return; } // the bike was hidden while the rider was typing: hide the box as at the start
      paintProblem(error.message);
    }
  }
  const later = () => { clearTimeout(timer); timer = setTimeout(refresh, 300); };

  function showUnavailable(answer) {
    // a battery pack or an accessory has nothing to compare: stay hidden; a bike without its figures says so
    if (!answer.applicable) { box.hidden = true; box.dataset.state = 'hidden'; return; }
    body.innerHTML = `<p class="muted" data-unavailable>${esc(answer.message || 'Chưa tính được cho xe này.')}</p>`;
    box.hidden = false; box.dataset.state = 'shown';
  }

  function showForm(first) {
    limits = {maxKmPerDay: first.maxKmPerDay, maxDaysPerMonth: first.maxDaysPerMonth};
    body.innerHTML = `<p class="muted">${'Nhập quãng đường bạn đi để so tiền sạc với tiền xăng của xe máy cùng phân khúc.'}</p>
      <div class="savings-inputs">
        <label>${'Số km mỗi ngày'}<input name="kmPerDay" type="number" inputmode="numeric" min="1" max="${limits.maxKmPerDay}" step="1" value="${DEFAULT_KM}" autocomplete="off"></label>
        <label>${'Số ngày đi mỗi tháng'}<input name="daysPerMonth" type="number" inputmode="numeric" min="1" max="${limits.maxDaysPerMonth}" step="1" value="${DEFAULT_DAYS}" autocomplete="off"></label>
      </div>
      <div data-result aria-live="polite"></div>`;
    body.querySelectorAll('input').forEach(input => input.addEventListener('input', later));
    paintResult(first);
    box.hidden = false; box.dataset.state = 'shown';
  }

  function showFailure(error) {
    if (isGone(error)) { box.hidden = true; box.dataset.state = 'hidden'; return; } // the bike was hidden or removed, or this account may not read products: nothing a retry could fix
    body.innerHTML = `<p class="installment-problem" role="alert">${esc(error.message)}</p><p><button type="button" class="button compact ghost" data-retry>${'Thử lại'}</button></p>`;
    body.querySelector('[data-retry]').addEventListener('click', event => { event.currentTarget.disabled = true; start(); }); // one try at a time
    box.hidden = false; box.dataset.state = 'shown';
  }

  async function start() {
    try {
      const first = await request(DEFAULT_KM, DEFAULT_DAYS);
      if (!first.calculable) { showUnavailable(first); return; }
      showForm(first);
    } catch (error) { showFailure(error); }
  }

  start();
})();
