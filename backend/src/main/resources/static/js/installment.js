// V24: simulated instalments. The calculator on the bike page, the application form at checkout, the application panel on an order
// (customer: pay the down payment; staff: approve or reject) and the staff's decisions from the queue. No real finance company is connected.
window.KineticInstallment = (() => {
  const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));
  const page = document.body.dataset.page || '';
  const money = value => window.KineticCart ? window.KineticCart.money(value) : new Intl.NumberFormat('vi-VN').format(Number(value) || 0) + ' ₫';
  const localeTag = () => document.documentElement.lang === 'en' ? 'en-GB' : 'vi-VN';
  const when = iso => { const d = new Date(iso); return isNaN(d) ? '—' : d.toLocaleString(localeTag(), {day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit'}); };
  const statusText = {PENDING: t('Chờ duyệt'), APPROVED: t('Đã duyệt'), REJECTED: t('Bị từ chối'), CANCELLED: t('Đã hủy')};
  const SIMULATION = t('Đây là công cụ mô phỏng: chưa kết nối công ty tài chính nào và không phát sinh khoản vay thật. Lãi suất phẳng theo năm, tính trên số tiền còn lại sau khi trả trước.');
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
      const error = Error(message || t('Không thực hiện được. Vui lòng thử lại sau.'));
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
    root.innerHTML = `<p class="muted">${t('Đang tải bảng lãi suất…')}</p>`;
    const view = () => ({percent: Number(root.querySelector('[name="down"]')?.value), term: Number(root.querySelector('[name="term"]:checked')?.value)});
    const paint = (result, problem) => {
      const box = root.querySelector('[data-result]');
      if (!box) return;
      if (problem) { box.innerHTML = `<p class="installment-problem" role="alert">${esc(problem)}</p>`; return; }
      if (!result) { box.innerHTML = `<p class="muted">${esc(emptyText || t('Chọn xe để xem khoản trả góp.'))}</p>`; return; }
      box.innerHTML = `<dl class="installment-figures">
        <div class="installment-main"><dt>${t('Trả mỗi tháng')}</dt><dd data-field="monthly">${esc(money(result.monthlyPayment))}</dd></div>
        <div><dt>${t('Trả trước')} (${result.downPaymentPercent}%)</dt><dd data-field="down">${esc(money(result.downPayment))}</dd></div>
        <div><dt>${t('Số tiền trả góp')}</dt><dd>${esc(money(result.financed))}</dd></div>
        <div><dt>${t('Lãi suất')}</dt><dd>${esc(String(result.annualRatePercent))}%/${t('năm')}</dd></div>
        <div><dt>${t('Tổng tiền lãi')}</dt><dd>${esc(money(result.totalInterest))}</dd></div>
        <div><dt>${t('Tổng phải trả')}</dt><dd data-field="total">${esc(money(result.totalPayable))}</dd></div></dl>
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
        root.innerHTML = `<p class="muted">${t('Hiện cửa hàng chưa áp dụng trả góp. Vui lòng chọn hình thức thanh toán khác.')}</p>`;
        onResult && onResult(null, true);
        return;
      }
      const start = Math.min(loaded.maxDownPaymentPercent, Math.max(loaded.minDownPaymentPercent, 30));
      root.innerHTML = `<label class="installment-slider">${t('Trả trước')} <b data-percent>${start}%</b>
          <input type="range" name="down" min="${loaded.minDownPaymentPercent}" max="${loaded.maxDownPaymentPercent}" step="5" value="${start}" aria-label="${esc(t('Phần trăm trả trước'))}"></label>
        <fieldset class="installment-terms"><legend>${t('Kỳ hạn')}</legend>${loaded.terms.map((term, index) => `<label><input type="radio" name="term" value="${term.termMonths}" ${index === loaded.terms.length - 1 ? 'checked' : ''}> ${term.termMonths} ${t('tháng')} <small>${esc(String(term.annualRatePercent))}%/${t('năm')}</small></label>`).join('')}</fieldset>
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
    note.textContent = SIMULATION + ' ' + t('Chưa gồm phí giao hàng, phí đăng ký và mã giảm giá; số tiền chính xác được tính ở bước thanh toán.');
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
        line.textContent = t('Khoản trả hàng tháng vượt {n}% thu nhập bạn khai. Hãy tăng khoản trả trước hoặc chọn kỳ hạn dài hơn.', {n: limits.maxPaymentPercentOfIncome});
      }
    };
    const build = async () => {
      try { limits = await config(); } catch (error) { holder.innerHTML = `<p class="installment-problem" role="alert">${esc(error.message)}</p>`; return; }
      holder.innerHTML = `<div class="installment-form"><div data-sim></div>
        <label>${t('Số CCCD')} <b>*</b><input name="idNumber" inputmode="numeric" autocomplete="off" maxlength="16" placeholder="${esc(t('12 chữ số'))}"></label>
        <label>${t('Thu nhập hằng tháng (VND)')} <b>*</b><input name="monthlyIncome" type="number" inputmode="numeric" min="0" step="100000" autocomplete="off"></label>
        <p class="installment-problem" data-affordability role="alert"></p>
        <p class="muted">${t('Cửa hàng chỉ lưu 4 số cuối của CCCD (các số còn lại được che) và mức thu nhập bạn khai, không lưu ảnh giấy tờ. Dữ liệu này bị xóa khi bạn xóa tài khoản.')}</p></div>`;
      holder.append(note);
      form = holder.querySelector('.installment-form');
      sim = simulator(form.querySelector('[data-sim]'), () => total, (result, failed) => {
        plan = result;
        simulatorReady = !failed;
        problem = failed ? t('Hiện chưa chọn được kỳ hạn trả góp. Vui lòng chọn hình thức thanh toán khác.') : result && !result.eligible ? result.reason : null;
        refreshAffordability();
      }, t('Chọn hình thức nhận hàng để tính khoản trả góp.'));
      form.querySelector('[name="monthlyIncome"]').addEventListener('input', refreshAffordability);
    };
    const identify = async () => {
      if (identified) return;
      identified = true;
      try {
        const response = await fetch('/api/v1/auth/profile', {credentials: 'same-origin', headers: {Accept: 'application/json'}});
        customer = response.ok ? ((await response.json()).roles || []).some(role => /CUSTOMER/.test(role)) : false;
      } catch (ignored) { customer = false; }
      if (customer) { await build(); if (total) sim && sim.refresh(); return; }
      // a visitor or a staff account: the application belongs to a customer account, so say so and offer the way in
      const returnTo = encodeURIComponent(location.pathname + location.search);
      holder.innerHTML = `<p class="installment-problem" role="alert">${t('Mua trả góp cần tài khoản khách hàng để theo dõi hồ sơ. Vui lòng đăng nhập bằng tài khoản khách hàng.')} <a href="/login?returnTo=${returnTo}">${t('Đăng nhập')}</a></p>`;
      holder.append(note);
    };
    return {
      show(on) { box.hidden = !on; if (on) identify(); },
      setTotal(value) { total = Number(value) || null; if (sim) sim.refresh(); },
      /** The text of what is missing or wrong, or null when the application can be sent. */
      error() {
        // the profile is still being asked for: a real customer must not be told to sign in
        if (customer === null) return t('Đang kiểm tra tài khoản. Vui lòng đợi một chút rồi đặt hàng lại.');
        if (!customer) return t('Mua trả góp cần tài khoản khách hàng. Vui lòng đăng nhập bằng tài khoản khách hàng hoặc chọn hình thức thanh toán khác.');
        if (!form || !simulatorReady) return t('Chưa tải được bảng trả góp. Vui lòng thử lại.');
        if (problem) return problem;
        if (!plan) return t('Đang tính khoản trả góp. Vui lòng đợi một chút.');
        if (!/^(\d{9}|\d{12})$/.test(form.elements.idNumber.value.replace(/[\s.\-]/g, ''))) return t('Số CCCD chưa hợp lệ. Vui lòng nhập 12 chữ số (hoặc 9 chữ số với chứng minh nhân dân cũ).');
        const income = Number(form.elements.monthlyIncome.value);
        if (!income || income < limits.minMonthlyIncome) return t('Vui lòng nhập thu nhập hằng tháng (tối thiểu {n}).', {n: money(limits.minMonthlyIncome)});
        if (plan.monthlyPayment > income * limits.maxPaymentPercentOfIncome / 100) return t('Khoản trả hàng tháng vượt {n}% thu nhập bạn khai. Hãy tăng khoản trả trước hoặc chọn kỳ hạn dài hơn.', {n: limits.maxPaymentPercentOfIncome});
        return null;
      },
      value() {
        const choice = sim.choice();
        return {idNumber: form.elements.idNumber.value.trim(), monthlyIncome: Number(form.elements.monthlyIncome.value), downPaymentPercent: choice.percent, termMonths: choice.term};
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
        <p class="dialog-actions"><button type="submit" class="button compact" data-submit>${esc(submit)}</button><button type="button" class="button compact ghost" data-cancel>${esc(t('Đóng'))}</button></p></form>`;
      document.body.appendChild(dialog);
      const form = dialog.querySelector('form'), error = dialog.querySelector('.support-message');
      let value = null;
      form.addEventListener('submit', event => {
        event.preventDefault();
        const text = form.elements.text ? form.elements.text.value.trim() : '';
        if (required && !text) { error.textContent = t('Vui lòng nhập nội dung.'); error.hidden = false; return; }
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
      title: approving ? t('Duyệt hồ sơ trả góp') : t('Từ chối hồ sơ trả góp'),
      intro: approving ? t('Khách sẽ nhận thông báo và email, rồi trả khoản trả trước qua VNPay hoặc tại cửa hàng. {s}', {s: summary || ''}) : t('Đơn hàng sẽ bị hủy và xe được trả về kho. Khách sẽ thấy lý do. {s}', {s: summary || ''}),
      label: approving ? t('Ghi chú cho khách (có thể để trống)') : t('Lý do từ chối (khách sẽ thấy)'),
      required: !approving, max: limits && (approving ? limits.maxApprovalNoteLength : limits.maxRejectionNoteLength), submit: approving ? t('Duyệt') : t('Từ chối')
    });
    if (text === null) return false;
    await request(`/api/v1/manager/installments/${Number(id)}`, 'PATCH', {decision, note: text});
    tell(approving ? t('Đã duyệt hồ sơ trả góp.') : t('Đã từ chối hồ sơ trả góp và hủy đơn hàng.'));
    return true;
  }

  function planTable(a) {
    const row = (name, value, field) => `<tr><td>${name}</td><td${field ? ` data-field="${field}"` : ''}>${esc(value)}</td></tr>`;
    return `<div class="table-scroll"><table><tbody>
      ${row(t('Giá trị đơn hàng'), money(a.orderTotal))}
      ${row(t('Trả trước') + ' (' + a.downPaymentPercent + '%)', money(a.downPayment), 'down')}
      ${row(t('Số tiền trả góp'), money(a.financed))}
      ${row(t('Kỳ hạn'), a.termMonths + ' ' + t('tháng'))}
      ${row(t('Lãi suất'), a.annualRatePercent + '%/' + t('năm'))}
      ${row(t('Trả mỗi tháng'), money(a.monthlyPayment), 'monthly')}
      ${row(t('Tổng phải trả'), money(a.totalPayable))}
      ${a.idNumberMasked ? row(t('Số CCCD'), a.idNumberMasked) : ''}
      ${a.monthlyIncome != null ? row(t('Thu nhập khai'), money(a.monthlyIncome)) : ''}
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
      if (a.status === 'PENDING') state = `<p>${staff ? t('Hồ sơ đang chờ duyệt.') : t('Hồ sơ đang chờ cửa hàng duyệt. Khi có kết quả bạn sẽ nhận thông báo và email.')}</p>`;
      else if (a.status === 'APPROVED') {
        state = `<p><strong>${t('Hồ sơ đã được duyệt.')}</strong> ${a.canPay ? t('Khoản trả trước cần thanh toán trước {when}, nếu không đơn hàng sẽ bị hủy.', {when: when(a.payBefore)}) : (a.paymentStatus === 'PAID' ? t('Khoản trả trước đã được thanh toán.') : '')}</p>`;
      } else if (a.status === 'REJECTED') state = `<p><strong>${t('Hồ sơ bị từ chối, đơn hàng đã được hủy.')}</strong></p>`;
      else state = `<p>${t('Hồ sơ đã đóng vì đơn hàng bị hủy.')}</p>`;
      const noteLine = a.reviewNote ? `<p><strong>${a.status === 'REJECTED' ? t('Lý do:') : t('Ghi chú của cửa hàng:')}</strong> ${esc(a.reviewNote)}</p>` : '';
      const who = staff && a.reviewedBy ? `<p class="muted">${t('Người xử lý:')} ${esc(a.reviewedBy)} · ${esc(when(a.reviewedAt))}</p>` : '';
      let actions = '';
      if (!staff && a.canPay) {
        actions = vnpayEnabled
          ? `<p><button type="button" class="button compact" data-pay>${t('Thanh toán khoản trả trước qua VNPay')}</button> <span class="muted">${t('hoặc trả tại cửa hàng')}</span></p>`
          : `<p class="muted">${t('Vui lòng trả khoản trả trước tại cửa hàng.')}</p>`;
      }
      if (canDecide && a.canDecide) {
        actions = `<p class="dialog-actions"><button type="button" class="button compact" data-decide="APPROVE">${t('Duyệt')}</button><button type="button" class="button compact ghost" data-decide="REJECT">${t('Từ chối')}</button></p>`;
      } else if (staff && !canDecide && a.status === 'PENDING') {
        actions = `<p class="muted">${t('Tài khoản của bạn chỉ có quyền xem hồ sơ trả góp.')}</p>`;
      }
      panel.innerHTML = `<h2>${t('Trả góp (mô phỏng)')} <span class="installment-status" data-status="${esc(a.status)}">${esc(statusText[a.status] || a.status)}</span></h2>${state}${noteLine}${who}${planTable(a)}${actions}<p class="muted">${SIMULATION}</p>`;
      panel.querySelector('[data-pay]')?.addEventListener('click', async event => {
        const button = event.currentTarget;
        button.disabled = true;
        try {
          const payment = await request('/api/v1/payments/vnpay/create', 'POST', {orderId, language: document.documentElement.lang === 'en' ? 'en' : 'vn'});
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
      viewOnly.textContent = t('Tài khoản của bạn chỉ có quyền xem hồ sơ trả góp.');
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
  return {checkout, simulator, config};
})();
