// V23: test rides. The booking dialog on the bike page, the customer's own bookings (cancel) and the manager's schedule (confirm, close, cancel).
window.KineticTestRide = (() => {
  const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));
  const page = document.body.dataset.page || '';
  const localeTag = () => document.documentElement.lang === 'en' ? 'en-GB' : 'vi-VN';
  const pad = n => String(n).padStart(2, '0');
  const isoDay = date => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
  const timeText = iso => new Date(iso).toLocaleTimeString(localeTag(), {hour: '2-digit', minute: '2-digit'});
  const dayLabel = date => date.toLocaleDateString(localeTag(), {weekday: 'long', day: '2-digit', month: '2-digit', year: 'numeric'});
  const DAYS_AHEAD = 14;
  const stepText = {CONFIRMED: t('Xác nhận'), DONE: t('Đã lái thử'), NO_SHOW: t('Khách không đến'), CANCELLED: t('Hủy lịch')};
  const tell = (message, error) => {
    const box = document.querySelector('#notice');
    if (!box) return;
    box.textContent = message;
    box.hidden = false;
    box.classList.toggle('error', Boolean(error));
  };

  /** Throws an Error with the server's message; a 401 sends the visitor to sign in and then back to {@code returnTo}. */
  async function request(url, method, body, returnTo) {
    const response = await fetch(url, {
      method: method || 'GET',
      credentials: 'same-origin',
      headers: {'Content-Type': 'application/json', Accept: 'application/json'},
      body: body === undefined ? undefined : JSON.stringify(body)
    });
    if (response.status === 401) {
      location.href = '/login?returnTo=' + encodeURIComponent(returnTo || location.pathname);
      throw Error(t('Vui lòng đăng nhập để tiếp tục.'));
    }
    if (!response.ok) {
      let message = '';
      try { message = (await response.json()).message || ''; } catch (ignored) { /* not JSON */ }
      const error = Error(message || t('Không thực hiện được. Vui lòng thử lại sau.'));
      error.status = response.status;
      throw error;
    }
    return response.status === 204 ? null : response.json();
  }

  /** A small dialog with an optional text box (staff note, reason) and a submit button. Resolves with the text ('' when there is no box), or null when cancelled. */
  function ask({title, intro, label, initial, required, max, submit}) {
    return new Promise(resolve => {
      const dialog = document.createElement('dialog');
      dialog.className = 'stock-dialog warranty-dialog';
      dialog.innerHTML = `<form novalidate><h2>${esc(title)}</h2>${intro ? `<p class="muted">${esc(intro)}</p>` : ''}
        ${label ? `<label>${esc(label)}<textarea name="text" rows="4" maxlength="${max || 1000}"></textarea></label>` : ''}
        <p class="support-message error" role="alert" hidden></p>
        <p class="dialog-actions"><button type="submit" class="button compact" data-submit>${esc(submit)}</button><button type="button" class="button compact ghost" data-cancel>${t('Hủy')}</button></p></form>`;
      document.body.appendChild(dialog);
      const form = dialog.querySelector('form'), error = dialog.querySelector('.support-message');
      if (form.elements.text) form.elements.text.value = initial || '';
      let value = null;
      form.addEventListener('submit', event => {
        event.preventDefault();
        const text = form.elements.text ? form.elements.text.value.trim() : '';
        if (required && !text) {
          error.textContent = t('Vui lòng nhập nội dung.');
          error.hidden = false;
          return;
        }
        value = text;
        dialog.close();
      });
      dialog.querySelector('[data-cancel]').addEventListener('click', () => dialog.close());
      dialog.addEventListener('close', () => { dialog.remove(); resolve(value); });
      dialog.showModal();
      (form.elements.text || dialog.querySelector('[data-submit]')).focus();
    });
  }

  // ---- bike page: the booking dialog ----
  async function book(product) {
    const dialog = document.createElement('dialog');
    dialog.className = 'stock-dialog warranty-dialog testride-dialog';
    const days = [];
    const today = new Date();
    for (let i = 0; i <= DAYS_AHEAD; i += 1) days.push(new Date(today.getFullYear(), today.getMonth(), today.getDate() + i));
    dialog.innerHTML = `<form novalidate><h2>${t('Đặt lịch lái thử')}</h2><p class="muted">${esc(product.name)}</p>
      <label>${t('Cửa hàng')}<select name="showroom" disabled><option value="">${t('Đang tải…')}</option></select></label>
      <label>${t('Ngày')}<select name="date">${days.map(day => `<option value="${isoDay(day)}">${esc(dayLabel(day))}</option>`).join('')}</select></label>
      <p class="muted testride-label">${t('Khung giờ (mỗi lượt 1 giờ)')}</p>
      <div class="testride-slots" role="group" aria-label="${esc(t('Khung giờ'))}" aria-live="polite"></div>
      <label>${t('Số điện thoại liên hệ')}<input name="phone" type="tel" inputmode="tel" autocomplete="tel" maxlength="15" placeholder="0901234567"></label>
      <label>${t('Ghi chú (không bắt buộc)')}<textarea name="note" rows="2" maxlength="500"></textarea></label>
      <p class="muted">${t('Vui lòng mang theo căn cước công dân. Bạn có thể hủy lịch trước giờ hẹn trong mục Lái thử của tài khoản.')}</p>
      <p class="support-message error" role="alert" hidden></p>
      <p class="dialog-actions"><button type="submit" class="button compact" data-submit disabled>${t('Gửi yêu cầu')}</button><button type="button" class="button compact ghost" data-cancel>${t('Hủy')}</button></p></form>`;
    document.body.appendChild(dialog);
    const form = dialog.querySelector('form'), slotsBox = dialog.querySelector('.testride-slots'), error = dialog.querySelector('.support-message');
    const submitButton = dialog.querySelector('[data-submit]');
    let chosen = null, seq = 0;
    const fail = message => { error.textContent = message; error.hidden = false; };
    const clearFail = () => { error.hidden = true; };

    /** Reloads the slots of the chosen showroom and day. {@code keepError} leaves the message on screen (a refusal that explains why the list is reloaded). */
    async function loadSlots(keepError) {
      const mine = ++seq;
      chosen = null;
      submitButton.disabled = true;
      if (keepError !== true) clearFail();
      if (!form.elements.showroom.value) { slotsBox.innerHTML = ''; return; }
      slotsBox.innerHTML = `<p class="muted">${t('Đang tải khung giờ…')}</p>`;
      let slots;
      try {
        slots = await request(`/api/v1/test-rides/slots?showroomId=${encodeURIComponent(form.elements.showroom.value)}&date=${encodeURIComponent(form.elements.date.value)}`, 'GET', undefined, location.pathname + '?testRide=1');
      } catch (err) {
        // a slower answer to an earlier choice must not replace the newest one
        if (mine !== seq) return;
        slotsBox.innerHTML = '';
        fail(err.message);
        return;
      }
      if (mine !== seq) return;
      if (!slots.length) {
        slotsBox.innerHTML = `<p class="muted">${t('Cửa hàng không nhận lịch trong ngày này.')}</p>`;
        return;
      }
      slotsBox.innerHTML = slots.map(slot => `<button type="button" class="testride-slot" data-start="${esc(slot.start)}" aria-pressed="false" ${slot.bookable ? '' : 'disabled'}><b>${esc(timeText(slot.start))}</b><small>${slot.remaining > 0 ? (slot.bookable ? t('Còn {n} chỗ', {n: slot.remaining}) : t('Đã qua giờ đặt')) : t('Đã đầy')}</small></button>`).join('');
      if (!slots.some(slot => slot.bookable)) slotsBox.insertAdjacentHTML('beforeend', `<p class="muted testride-none">${t('Không còn khung giờ nào trong ngày này. Vui lòng chọn ngày khác.')}</p>`);
    }

    slotsBox.addEventListener('click', event => {
      const button = event.target.closest('.testride-slot');
      if (!button || button.disabled) return;
      slotsBox.querySelectorAll('.testride-slot').forEach(node => { node.classList.remove('selected'); node.setAttribute('aria-pressed', 'false'); });
      button.classList.add('selected');
      button.setAttribute('aria-pressed', 'true');
      chosen = button.dataset.start;
      submitButton.disabled = false;
      clearFail();
    });
    form.elements.showroom.addEventListener('change', () => loadSlots());
    form.elements.date.addEventListener('change', () => loadSlots());
    dialog.querySelector('[data-cancel]').addEventListener('click', () => dialog.close());
    dialog.addEventListener('close', () => dialog.remove());

    form.addEventListener('submit', async event => {
      event.preventDefault();
      if (submitButton.disabled) return;
      if (!chosen) { fail(t('Vui lòng chọn khung giờ.')); return; }
      const phone = form.elements.phone.value.replace(/[\s.\-]/g, '');
      if (!/^0\d{9,10}$/.test(phone)) { fail(t('Số điện thoại chưa hợp lệ. Vui lòng nhập số bắt đầu bằng 0 và gồm 10-11 chữ số.')); form.elements.phone.focus(); return; }
      submitButton.disabled = true;
      clearFail();
      try {
        const saved = await request('/api/v1/test-rides', 'POST', {
          productId: product.id, showroomId: Number(form.elements.showroom.value), slotStart: chosen, contactPhone: phone, note: form.elements.note.value.trim()
        }, location.pathname + '?testRide=1');
        form.innerHTML = `<h2>${t('Đã gửi yêu cầu lái thử')}</h2>
          <p data-field="done">${esc(saved.productName)} · ${esc(saved.showroomName)} · ${esc(new Date(saved.slotStart).toLocaleString(localeTag(), {dateStyle: 'full', timeStyle: 'short'}))}</p>
          <p class="muted">${t('Cửa hàng sẽ xác nhận lịch và báo cho bạn qua thông báo trong tài khoản (và email nếu bạn bật nhận thông báo).')}</p>
          <p class="dialog-actions"><a class="button compact" href="/customer/test-rides">${t('Xem lịch của tôi')}</a><button type="button" class="button compact ghost" data-close>${t('Đóng')}</button></p>`;
        form.querySelector('[data-close]').addEventListener('click', () => dialog.close());
      } catch (err) {
        fail(err.message);
        // the slot may have just filled up: show the newest places
        if (err.status === 409) loadSlots(true); else submitButton.disabled = !chosen;
      }
    });

    dialog.showModal();
    form.elements.date.focus();
    try {
      const showrooms = await request('/api/v1/showrooms', 'GET', undefined, location.pathname + '?testRide=1');
      const select = form.elements.showroom;
      if (!showrooms.length) {
        select.innerHTML = `<option value="">${t('Chưa có cửa hàng nào')}</option>`;
        fail(t('Chưa có cửa hàng nào nhận lịch lái thử.'));
        return;
      }
      select.innerHTML = showrooms.map(room => `<option value="${room.id}">${esc(room.name)}</option>`).join('');
      select.disabled = false;
      loadSlots();
    } catch (err) {
      form.elements.showroom.innerHTML = `<option value="">—</option>`;
      fail(err.message);
    }
  }

  function setupBikePage() {
    const root = document.querySelector('#productPage');
    const button = document.querySelector('#bookTestRide');
    if (!root) return;
    const wanted = new URLSearchParams(location.search).get('testRide');
    if (wanted) { try { history.replaceState(null, '', location.pathname); } catch (ignored) { /* keep the address */ } }
    if (!button) return;
    const product = {id: Number(root.dataset.id), name: root.dataset.name};
    const isCustomer = root.dataset.customer === 'true';
    const open = () => {
      // a visitor signs in first and comes back to this page with the dialog open
      if (!isCustomer) { location.href = '/login?returnTo=' + encodeURIComponent(location.pathname + '?testRide=1'); return; }
      book(product);
    };
    button.addEventListener('click', open);
    if (wanted && isCustomer) book(product);
  }

  // ---- customer: own bookings ----
  function setupCustomer(table) {
    const lead = document.querySelector('.work-heading p:last-child');
    if (lead) lead.insertAdjacentHTML('afterend', `<p class="muted">${t('Muốn lái thử xe nào? Mở trang xe và bấm "Đặt lịch lái thử".')} <a href="/products">${t('Xem các xe')}</a></p>`);
    table.addEventListener('click', async event => {
      const button = event.target.closest('[data-action="ride-cancel"]');
      if (!button) return;
      const sure = await ask({title: t('Hủy lịch lái thử'), intro: button.dataset.summary || '', submit: t('Hủy lịch')});
      if (sure === null) return;
      button.disabled = true;
      try {
        await request(`/api/v1/test-rides/${Number(button.dataset.id)}/cancel`, 'PATCH');
        tell(t('Đã hủy lịch lái thử.'));
      } catch (err) {
        tell(err.message, true);
      } finally {
        // reload either way: a refusal usually means the booking changed meanwhile, so show it as it is now
        window.dispatchEvent(new Event('workspace:reload'));
      }
    });
  }

  // ---- manager: schedule ----
  function setupManager(table) {
    // the showroom filter lists the active showrooms (the options are filled once they arrive)
    fetch('/api/v1/showrooms', {credentials: 'same-origin', headers: {Accept: 'application/json'}})
      .then(response => response.ok ? response.json() : [])
      .then(rooms => {
        const select = document.querySelector('#filters select[name="showroomId"]');
        if (select) rooms.forEach(room => select.insertAdjacentHTML('beforeend', `<option value="${room.id}">${esc(room.name)}</option>`));
      })
      .catch(() => { /* the filter stays on "all showrooms" */ });
    table.addEventListener('click', async event => {
      const button = event.target.closest('[data-action="ride-step"]');
      if (!button) return;
      const status = button.dataset.status;
      const cancelling = status === 'CANCELLED';
      const note = await ask({
        title: status ? (stepText[status] || status) : t('Sửa ghi chú'),
        intro: t('Ghi chú này khách sẽ thấy trong mục Lái thử của tài khoản.'),
        label: cancelling ? t('Lý do hủy (khách sẽ thấy)') : t('Ghi chú cho khách (có thể để trống)'),
        initial: button.dataset.note || '',
        required: cancelling, max: 1000, submit: t('Cập nhật')
      });
      if (note === null) return;
      button.disabled = true;
      try {
        await request(`/api/v1/manager/test-rides/${Number(button.dataset.id)}`, 'PATCH', status ? {status, staffNote: note} : {staffNote: note});
        tell(t('Đã cập nhật lịch lái thử.'));
      } catch (err) {
        tell(err.message, true);
      } finally {
        window.dispatchEvent(new Event('workspace:reload'));
      }
    });
  }

  const table = document.querySelector('#data-table');
  if (table && page === '/customer/test-rides') setupCustomer(table);
  if (table && page === '/manager/test-rides') setupManager(table);
  if (document.querySelector('#productPage')) setupBikePage();
  return {book};
})();
