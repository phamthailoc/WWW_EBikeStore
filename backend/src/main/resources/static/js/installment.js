// V24: simulated instalments. The calculator on the bike page, the application form at checkout, the application panel on an order
// (customer: pay the down payment; staff: approve or reject) and the staff's decisions from the queue. No real finance company is connected.
window.KineticInstallment = (() => {
  const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));
  const page = document.body.dataset.page || '';
  const money = value => window.KineticCart ? window.KineticCart.money(value) : new Intl.NumberFormat('vi-VN').format(Number(value) || 0) + ' ₫';
  const localeTag = () => 'vi-VN';
  // the rate comes from a DECIMAL column ("8.00"): shown as 8 or 8.5 (8,5 in Vietnamese), never with trailing zeros
  const rate = value => new Intl.NumberFormat(localeTag(), {maximumFractionDigits: 2}).format(Number(value) || 0);
  // The server sends a moment with the shop's offset ("2026-11-20T09:00:00+07:00"). It is shown in that offset, with the offset named like the notice and the
  // email do, so a browser in another zone reads the same clock time as the email for the same deadline.
  const when = iso => {
    const d = new Date(iso);
    if (isNaN(d)) return '—';
    const format = {day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit'};
    const match = /([+-])(\d{2}):?(\d{2})$/.exec(String(iso));
    const minutes = /Z$/.test(String(iso)) ? 0 : match ? (match[1] === '-' ? -1 : 1) * (Number(match[2]) * 60 + Number(match[3])) : null;
    if (minutes === null) return d.toLocaleString(localeTag(), format);
    const abs = Math.abs(minutes);
    const label = ' (GMT' + (minutes < 0 ? '-' : '+') + Math.floor(abs / 60) + (abs % 60 ? ':' + String(abs % 60).padStart(2, '0') : '') + ')';
    return new Date(d.getTime() + minutes * 60000).toLocaleString(localeTag(), {...format, timeZone: 'UTC'}) + label;
  };
  const statusText = {PENDING: 'Chờ duyệt', APPROVED: 'Đã duyệt', REJECTED: 'Bị từ chối', CANCELLED: 'Đã hủy'};
  const SIMULATION = 'Đây là công cụ mô phỏng: chưa kết nối công ty tài chính nào và không phát sinh khoản vay thật. Lãi suất phẳng theo năm, tính trên số tiền còn lại sau khi trả trước.';
  const tell = (message, error) => {
    const box = document.querySelector('#notice');
    if (!box) return;
    box.textContent = message;
    box.hidden = false;
    box.classList.toggle('error', Boolean(error));
  };

  /** Throws an Error with the server's message (and its status). */
  async function request(url, method, body) {
    const response = await fetch(url, {
      method: method || 'GET',
      credentials: 'same-origin',
      headers: {'Content-Type': 'application/json', Accept: 'application/json'},
      body: body === undefined ? undefined : JSON.stringify(body)
    });
    if (!response.ok) {
      let message = '';
      try { message = (await response.json()).message || ''; } catch (ignored) { /* not JSON */ }
      const error = Error(message || 'Không thực hiện được. Vui lòng thử lại sau.');
      error.status = response.status;
      throw error;
    }
    return response.status === 204 ? null : response.json();
  }

  // the offer changes only when an admin edits a rate: one answer per page view is enough
  let configRequest = null;
  const config = () => {
    if (!configRequest) configRequest = request('/api/v1/installments/config').catch(error => { configRequest = null; throw error; });
    return configRequest;
  };
  const quote = (amount, percent, term) => request(`/api/v1/installments/quote?amount=${encodeURIComponent(Math.round(amount))}&downPaymentPercent=${percent}&termMonths=${term}`);

  /**
   * The sliders and the figures, shared by the bike page and checkout. {@code amount()} says what is being financed (null: not known yet); the figures
   * follow the slider and the term after a short pause, and an answer that arrives late for an older choice is dropped.
   */
  function simulator(root, amount, onResult, emptyText) {
    let offer = null, sequence = 0, timer = null, last = null;
    root.innerHTML = `<p class="muted">${'Đang tải bảng lãi suất…'}</p>`;
    const view = () => ({percent: Number(root.querySelector('[name="down"]')?.value), term: Number(root.querySelector('[name="term"]:checked')?.value)});
    const paint = (result, problem) => {
      const box = root.querySelector('[data-result]');
      if (!box) return;
      if (problem) { box.innerHTML = `<p class="installment-problem" role="alert">${esc(problem)}</p>`; return; }
      if (!result) { box.innerHTML = `<p class="muted">${esc(emptyText || 'Chọn xe để xem khoản trả góp.')}</p>`; return; }
      box.innerHTML = `<dl class="installment-figures">
        <div class="installment-main"><dt>${'Trả mỗi tháng'}</dt><dd data-field="monthly">${esc(money(result.monthlyPayment))}</dd></div>
        <div><dt>${'Trả trước'} (${result.downPaymentPercent}%)</dt><dd data-field="down">${esc(money(result.downPayment))}</dd></div>
        <div><dt>${'Số tiền trả góp'}</dt><dd>${esc(money(result.financed))}</dd></div>
        <div><dt>${'Lãi suất'}</dt><dd>${esc(rate(result.annualRatePercent))}%/${'năm'}</dd></div>
        <div><dt>${'Tổng tiền lãi'}</dt><dd>${esc(money(result.totalInterest))}</dd></div>
        <div><dt>${'Tổng phải trả'}</dt><dd data-field="total">${esc(money(result.totalPayable))}</dd></div></dl>
        ${result.eligible ? '' : `<p class="installment-problem" role="alert">${esc(result.reason || '')}</p>`}`;
    };
    async function refresh() {
      const mine = ++sequence;
      const value = amount();
      if (!offer || !value || value <= 0) { last = null; paint(null); onResult && onResult(null); return; }
      const choice = view();
      try {
        const result = await quote(value, choice.percent, choice.term);
        if (mine !== sequence) return;
        last = result;
        paint(result);
        onResult && onResult(result);
      } catch (error) {
        if (mine !== sequence) return;
        last = null;
        paint(null, error.message);
        onResult && onResult(null);
      }
    }
    const later = () => { clearTimeout(timer); timer = setTimeout(refresh, 250); };
    config().then(loaded => {
      offer = loaded;
      if (!loaded.terms.length) {
        root.innerHTML = `<p class="muted">${'Hiện cửa hàng chưa áp dụng trả góp. Vui lòng chọn hình thức thanh toán khác.'}</p>`;
        onResult && onResult(null, true);
        return;
      }
      const start = Math.min(loaded.maxDownPaymentPercent, Math.max(loaded.minDownPaymentPercent, 30));
      root.innerHTML = `<label class="installment-slider">${'Trả trước'} <b data-percent>${start}%</b>
          <input type="range" name="down" min="${loaded.minDownPaymentPercent}" max="${loaded.maxDownPaymentPercent}" step="5" value="${start}" aria-label="${esc('Phần trăm trả trước')}"></label>
        <fieldset class="installment-terms"><legend>${'Kỳ hạn'}</legend>${loaded.terms.map((term, index) => `<label><input type="radio" name="term" value="${term.termMonths}" ${index === loaded.terms.length - 1 ? 'checked' : ''}> ${term.termMonths} ${'tháng'} <small>${esc(rate(term.annualRatePercent))}%/${'năm'}</small></label>`).join('')}</fieldset>
        <div data-result aria-live="polite"></div>`;
      root.querySelector('[name="down"]').addEventListener('input', event => {
        root.querySelector('[data-percent]').textContent = event.target.value + '%';
        later();
      });
      root.querySelectorAll('[name="term"]').forEach(radio => radio.addEventListener('change', later));
      refresh();
    }).catch(error => {
      root.innerHTML = `<p class="installment-problem" role="alert">${esc(error.message)}</p>`;
      onResult && onResult(null, true);
    });
    return {refresh: later, plan: () => last, choice: view};
  }

  // ---- bike page: the calculator ----
  function setupBikePage() {
    const box = document.querySelector('#installmentBox');
    if (!box || !window.kineticProductAmount) return;
    const body = box.querySelector('[data-body]');
    const sim = simulator(body, () => window.kineticProductAmount());
    // the figures are on the price of the bike alone: the exact amount is worked out at checkout, with delivery, registration and a discount code
    const note = document.createElement('p');
    note.className = 'muted';
    note.textContent = SIMULATION + ' ' + 'Chưa gồm phí giao hàng, phí đăng ký và mã giảm giá; số tiền chính xác được tính ở bước thanh toán.';
    box.append(note);
    window.addEventListener('kinetic:amount', () => sim.refresh());
    box.hidden = false;
  }

  // ---- checkout: the application ----
  function checkout(box) {
    box.innerHTML = '';
    let total = null, customer = null, identified = false, plan = null, simulatorReady = false, problem = null;
    const holder = document.createElement('div');
    box.append(holder);
    const note = document.createElement('p');
    note.className = 'muted';
    note.textContent = SIMULATION;
    let form = null, sim = null, limits = null;

    const refreshAffordability = () => {
      const line = box.querySelector('[data-affordability]');
      if (!line || !limits) return;
      const income = Number(box.querySelector('[name="monthlyIncome"]')?.value);
      line.textContent = '';
      if (plan && income > 0 && plan.monthlyPayment > income * limits.maxPaymentPercentOfIncome / 100) {
        line.textContent = `Khoản trả hàng tháng vượt ${limits.maxPaymentPercentOfIncome}% thu nhập bạn khai. Hãy tăng khoản trả trước hoặc chọn kỳ hạn dài hơn.`;
      }
    };
    // a hidden box must not hold invalid fields: the browser would refuse the whole checkout form without showing why
    const setEnabled = on => box.querySelectorAll('input, textarea, select').forEach(field => { field.disabled = !on; });
    const build = async () => {
      try { limits = await config(); } catch (error) {
        // let the next try start again instead of leaving the box without a form until the page is reloaded
        identified = false;
        holder.innerHTML = `<p class="installment-problem" role="alert">${esc(error.message)}</p><p><button type="button" class="button compact ghost" data-retry>${'Thử lại'}</button></p>`;
        holder.querySelector('[data-retry]').addEventListener('click', () => identify());
        return;
      }
      holder.innerHTML = `<div class="installment-form"><div data-sim></div>
        <label>${'Số CCCD'} <b>*</b><input name="idNumber" inputmode="numeric" autocomplete="off" maxlength="16" placeholder="${esc('12 chữ số')}"></label>
        <label>${'Thu nhập hằng tháng (VND)'} <b>*</b><input name="monthlyIncome" type="number" inputmode="numeric" min="0" step="any" autocomplete="off"></label>
        <p class="installment-problem" data-affordability role="alert"></p>
        <p class="muted">${'Cửa hàng chỉ lưu 4 số cuối của CCCD (các số còn lại được che) và mức thu nhập bạn khai, không lưu ảnh giấy tờ. Dữ liệu này bị xóa khi bạn xóa tài khoản.'}</p></div>`;
      holder.append(note);
      form = holder.querySelector('.installment-form');
      sim = simulator(form.querySelector('[data-sim]'), () => total, (result, failed) => {
        plan = result;
        simulatorReady = !failed;
        problem = failed ? 'Hiện chưa chọn được kỳ hạn trả góp. Vui lòng chọn hình thức thanh toán khác.' : result && !result.eligible ? result.reason : null;
        refreshAffordability();
      }, 'Chọn hình thức nhận hàng để tính khoản trả góp.');
      form.querySelector('[name="monthlyIncome"]').addEventListener('input', refreshAffordability);
      setEnabled(!box.hidden);
    };
    const identify = async () => {
      if (identified) return;
      identified = true;
      let unreachable = false;
      try {
        const response = await fetch('/api/v1/auth/profile', {credentials: 'same-origin', headers: {Accept: 'application/json'}});
        // a customer account only: an account that also has a staff role may not apply for credit
        if (response.ok) {
          customer = (roles => roles.length > 0 && roles.every(role => role === 'CUSTOMER' || role === 'ROLE_CUSTOMER'))((await response.json()).roles || []);
        } else if (response.status === 401 || response.status === 403) {
          customer = false;
        } else { unreachable = true; }
      } catch (ignored) { unreachable = true; }
      if (unreachable) {
        // a network error or a 5xx says nothing about the account: offer another try instead of asking the customer to sign in
        identified = false;
        holder.innerHTML = `<p class="installment-problem" role="alert">${'Chưa kiểm tra được tài khoản của bạn. Vui lòng thử lại.'} <button type="button" class="installment-retry">${'Thử lại'}</button></p>`;
        holder.querySelector('.installment-retry').addEventListener('click', () => identify());
        return;
      }
      if (customer) { await build(); if (total) sim && sim.refresh(); return; }
      // a visitor or a staff account: the application belongs to a customer account, so say so and offer the way in
      const returnTo = encodeURIComponent(location.pathname + location.search);
      holder.innerHTML = `<p class="installment-problem" role="alert">${'Mua trả góp cần tài khoản khách hàng để theo dõi hồ sơ. Vui lòng đăng nhập bằng tài khoản khách hàng.'} <a href="/login?returnTo=${returnTo}">${'Đăng nhập'}</a></p>`;
      holder.append(note);
    };
    return {
      show(on) { box.hidden = !on; setEnabled(on); if (on) identify(); },
      setTotal(value) { total = Number(value) || null; if (sim) sim.refresh(); },
      /** The text of what is missing or wrong, or null when the application can be sent. */
      error() {
        // the profile is still being asked for: a real customer must not be told to sign in
        if (customer === null) return 'Đang kiểm tra tài khoản. Vui lòng đợi một chút rồi đặt hàng lại.';
        if (!customer) return 'Mua trả góp cần tài khoản khách hàng. Vui lòng đăng nhập bằng tài khoản khách hàng hoặc chọn hình thức thanh toán khác.';
        if (!form || !simulatorReady) return 'Chưa tải được bảng trả góp. Vui lòng thử lại.';
        if (problem) return problem;
        if (!plan) return 'Đang tính khoản trả góp. Vui lòng đợi một chút.';
        if (!/^(\d{9}|\d{12})$/.test(form.querySelector('[name=idNumber]').value.replace(/[\s.\-]/g, ''))) return 'Số CCCD chưa hợp lệ. Vui lòng nhập 12 chữ số (hoặc 9 chữ số với chứng minh nhân dân cũ).';
        const income = Number(form.querySelector('[name=monthlyIncome]').value);
        if (!income || income < limits.minMonthlyIncome) return `Vui lòng nhập thu nhập hằng tháng (tối thiểu ${money(limits.minMonthlyIncome)}).`;
        if (limits.maxMonthlyIncome && income > limits.maxMonthlyIncome) return `Thu nhập hằng tháng tối đa ${money(limits.maxMonthlyIncome)}.`;
        if (!Number.isInteger(income)) return 'Thu nhập hằng tháng phải là số đồng nguyên, không có phần thập phân.';
        if (plan.monthlyPayment > income * limits.maxPaymentPercentOfIncome / 100) return `Khoản trả hàng tháng vượt ${limits.maxPaymentPercentOfIncome}% thu nhập bạn khai. Hãy tăng khoản trả trước hoặc chọn kỳ hạn dài hơn.`;
        return null;
      },
      value() {
        const choice = sim.choice();
        return {idNumber: form.querySelector('[name=idNumber]').value.trim(), monthlyIncome: Number(form.querySelector('[name=monthlyIncome]').value), downPaymentPercent: choice.percent, termMonths: choice.term};
      }
    };
  }

  // ---- the application on an order page ----
  /** Staff's dialog with an optional text box: resolves with the text ('' when there is no box), or null when it was cancelled. */
  function ask({title, intro, label, required, max, submit}) {
    return new Promise(resolve => {
      const dialog = document.createElement('dialog');
      dialog.className = 'stock-dialog warranty-dialog';
      dialog.innerHTML = `<form novalidate><h2>${esc(title)}</h2>${intro ? `<p class="muted">${esc(intro)}</p>` : ''}
        ${label ? `<label>${esc(label)}<textarea name="text" rows="4" maxlength="${max || 1000}"></textarea></label>` : ''}
        <p class="support-message error" role="alert" hidden></p>
        <p class="dialog-actions"><button type="submit" class="button compact" data-submit>${esc(submit)}</button><button type="button" class="button compact ghost" data-cancel>${esc('Đóng')}</button></p></form>`;
      document.body.appendChild(dialog);
      const form = dialog.querySelector('form'), error = dialog.querySelector('.support-message');
      let value = null;
      form.addEventListener('submit', event => {
        event.preventDefault();
        const text = form.elements.text ? form.elements.text.value.trim() : '';
        if (required && !text) { error.textContent = 'Vui lòng nhập nội dung.'; error.hidden = false; return; }
        value = text;
        dialog.close();
      });
      dialog.querySelector('[data-cancel]').addEventListener('click', () => dialog.close());
      dialog.addEventListener('close', () => { dialog.remove(); resolve(value); });
      dialog.showModal();
      (form.elements.text || dialog.querySelector('[data-submit]')).focus();
    });
  }

  async function decide(id, decision, summary) {
    const approving = decision === 'APPROVE';
    // the length of the note is the server's to say (the rejection note shares the order's cancellation reason); without the config the form leaves it to the server
    const limits = await config().catch(() => null);
    const text = await ask({
      title: approving ? 'Duyệt hồ sơ trả góp' : 'Từ chối hồ sơ trả góp',
      intro: approving ? `Khách sẽ nhận thông báo và email, rồi trả khoản trả trước qua VNPay hoặc tại cửa hàng. ${summary || ''}` : `Đơn hàng sẽ bị hủy và xe được trả về kho. Khách sẽ thấy lý do. ${summary || ''}`,
      label: approving ? 'Ghi chú cho khách (có thể để trống)' : 'Lý do từ chối (khách sẽ thấy)',
      required: !approving, max: limits && (approving ? limits.maxApprovalNoteLength : limits.maxRejectionNoteLength), submit: approving ? 'Duyệt' : 'Từ chối'
    });
    if (text === null) return false;
    await request(`/api/v1/manager/installments/${Number(id)}`, 'PATCH', {decision, note: text});
    tell(approving ? 'Đã duyệt hồ sơ trả góp.' : 'Đã từ chối hồ sơ trả góp và hủy đơn hàng.');
    return true;
  }

  function planTable(a) {
    const row = (name, value, field) => `<tr><td>${name}</td><td${field ? ` data-field="${field}"` : ''}>${esc(value)}</td></tr>`;
    return `<div class="table-scroll"><table><tbody>
      ${row('Giá trị đơn hàng', money(a.orderTotal))}
      ${row('Trả trước' + ' (' + a.downPaymentPercent + '%)', money(a.downPayment), 'down')}
      ${row('Số tiền trả góp', money(a.financed))}
      ${row('Kỳ hạn', a.termMonths + ' ' + 'tháng')}
      ${row('Lãi suất', rate(a.annualRatePercent) + '%/' + 'năm')}
      ${row('Trả mỗi tháng', money(a.monthlyPayment), 'monthly')}
      ${row('Tổng phải trả', money(a.totalPayable))}
      ${a.idNumberMasked ? row('Số CCCD', a.idNumberMasked) : ''}
      ${a.monthlyIncome != null ? row('Thu nhập khai', money(a.monthlyIncome)) : ''}
    </tbody></table></div>`;
  }

  function setupOrderPage() {
    const match = /^\/(customer|manager)\/orders\/(\d+)$/.exec(location.pathname);
    if (!match) return;
    const staff = match[1] === 'manager';
    const orderId = Number(match[2]);
    const canDecide = staff && document.body.dataset.canUpdate === 'true';
    const vnpayEnabled = document.body.dataset.vnpayEnabled === 'true';
    const panel = document.createElement('section');
    panel.id = 'installmentPanel';
    panel.className = 'data-panel';
    panel.style.marginTop = '20px';
    panel.hidden = true;
    let attached = false;

    async function load() {
      let a;
      try {
        a = await request(`/api/v1/installments/orders/${orderId}`);
      } catch (error) {
        panel.hidden = true;
        if (error.status && error.status !== 403) tell(error.message, true);
        return;
      }
      // 204: the order is not paid by instalments (or is not this customer's): no panel
      if (!a) { panel.hidden = true; return; }
      if (!attached) { document.querySelector('.work-main').appendChild(panel); attached = true; }
      panel.hidden = false;
      let state;
      if (a.status === 'PENDING') {
        // the order is cancelled when the store has not decided in time: both sides are told by when
        const lapsed = a.reviewBefore && new Date(a.reviewBefore) < new Date();
        const deadline = !a.reviewBefore ? '' : lapsed ? 'Đã quá thời hạn duyệt, đơn hàng sắp bị hủy tự động.' : `Nếu cửa hàng chưa duyệt trước ${when(a.reviewBefore)}, đơn hàng sẽ bị hủy tự động.`;
        state = `<p>${staff ? 'Hồ sơ đang chờ duyệt.' : 'Hồ sơ đang chờ cửa hàng duyệt. Khi có kết quả bạn sẽ nhận thông báo và email.'} ${esc(deadline)}</p>`;
      }
      else if (a.status === 'APPROVED') {
        state = `<p><strong>${'Hồ sơ đã được duyệt.'}</strong> ${a.canPay ? `Khoản trả trước cần thanh toán trước ${when(a.payBefore)}, nếu không đơn hàng sẽ bị hủy.` : (a.paymentStatus === 'PAID' ? 'Khoản trả trước đã được thanh toán.' : (a.payBefore ? 'Đã quá hạn thanh toán khoản trả trước, đơn hàng sắp bị hủy tự động.' : ''))}</p>`;
      } else if (a.status === 'REJECTED') state = `<p><strong>${'Hồ sơ bị từ chối, đơn hàng đã được hủy.'}</strong></p>`;
      else state = `<p>${'Hồ sơ đã đóng vì đơn hàng bị hủy.'}</p>`;
      const noteLine = a.reviewNote ? `<p><strong>${a.status === 'REJECTED' ? 'Lý do:' : 'Ghi chú của cửa hàng:'}</strong> ${esc(a.reviewNote)}</p>` : '';
      const who = staff && a.reviewedBy ? `<p class="muted">${'Người xử lý:'} ${esc(a.reviewedBy)} · ${esc(when(a.reviewedAt))}</p>` : '';
      let actions = '';
      if (!staff && a.canPay) {
        actions = vnpayEnabled
          ? `<p><button type="button" class="button compact" data-pay>${'Thanh toán khoản trả trước qua VNPay'}</button> <span class="muted">${'hoặc trả tại cửa hàng'}</span></p>`
          : `<p class="muted">${'Vui lòng trả khoản trả trước tại cửa hàng.'}</p>`;
      }
      if (canDecide && a.canDecide) {
        actions = `<p class="dialog-actions"><button type="button" class="button compact" data-decide="APPROVE">${'Duyệt'}</button><button type="button" class="button compact ghost" data-decide="REJECT">${'Từ chối'}</button></p>`;
      } else if (staff && !canDecide && a.status === 'PENDING') {
        actions = `<p class="muted">${'Tài khoản của bạn chỉ có quyền xem hồ sơ trả góp.'}</p>`;
      }
      panel.innerHTML = `<h2>${'Trả góp (mô phỏng)'} <span class="installment-status" data-status="${esc(a.status)}">${esc(statusText[a.status] || a.status)}</span></h2>${state}${noteLine}${who}${planTable(a)}${actions}<p class="muted">${SIMULATION}</p>`;
      panel.querySelector('[data-pay]')?.addEventListener('click', async event => {
        const button = event.currentTarget;
        button.disabled = true;
        try {
          const payment = await request('/api/v1/payments/vnpay/create', 'POST', {orderId});
          location.href = payment.paymentUrl;
        } catch (error) {
          tell(error.message, true);
          button.disabled = false;
        }
      });
      panel.querySelectorAll('[data-decide]').forEach(button => button.addEventListener('click', async () => {
        button.disabled = true;
        try {
          if (await decide(a.id, button.dataset.decide, a.orderNumber)) window.dispatchEvent(new Event('workspace:reload'));
        } catch (error) {
          tell(error.message, true);
          // a refusal usually means the application changed meanwhile: show it as it is now
          window.dispatchEvent(new Event('workspace:reload'));
        } finally {
          button.disabled = false;
        }
      }));
    }
    load();
    window.addEventListener('workspace:reload', load);
  }

  // ---- manager: the queue ----
  function setupQueue(table) {
    if (document.body.dataset.canUpdate !== 'true') {
      // the server refuses a decision from this account, so the page offers none
      const viewOnly = document.createElement('p');
      viewOnly.className = 'muted';
      viewOnly.textContent = 'Tài khoản của bạn chỉ có quyền xem hồ sơ trả góp.';
      table.parentNode.insertBefore(viewOnly, table);
    }
    table.addEventListener('click', async event => {
      const button = event.target.closest('[data-action="installment-decide"]');
      if (!button) return;
      button.disabled = true;
      try {
        await decide(button.dataset.id, button.dataset.decision, button.dataset.summary);
      } catch (error) {
        tell(error.message, true);
      } finally {
        // reload either way: a refusal usually means the application changed meanwhile, so show it as it is now
        window.dispatchEvent(new Event('workspace:reload'));
      }
    });
  }

  const table = document.querySelector('#data-table');
  if (table && page === '/manager/installments') setupQueue(table);
  if (document.querySelector('#productPage')) setupBikePage();
  if (document.body.classList.contains('workspace') && /^\/(customer|manager)\/orders\/\d+$/.test(location.pathname)) setupOrderPage();
  return {checkout, simulator, config, when};
})();
