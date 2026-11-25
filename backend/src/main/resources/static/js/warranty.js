// V20: warranty book. Customer page (requests panel and the request form), manager queue (status steps) and the public frame-number lookup.
window.KineticWarranty = (() => {
  const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));
  const page = document.body.dataset.page || '';
  const statusText = {NEW: 'Mới gửi', RECEIVED: 'Đã tiếp nhận', REPAIRING: 'Đang sửa', DONE: 'Đã hoàn tất', REJECTED: 'Bị từ chối'};
  const coverageText = {ACTIVE: 'Còn bảo hành', EXPIRED: 'Hết bảo hành', UNKNOWN: 'Chưa có thời hạn bảo hành', NOT_STARTED: 'Chưa bàn giao'};
  const day = iso => {
    if (!iso) return '-';
    const [y, m, d] = String(iso).slice(0, 10).split('-').map(Number);
    return new Date(y, m - 1, d).toLocaleDateString('vi-VN');
  };
  const tell = (message, error) => {
    const box = document.querySelector('#notice');
    if (!box) return;
    box.textContent = message;
    box.hidden = false;
    box.classList.toggle('error', Boolean(error));
  };

  async function request(url, method, body) {
    const response = await fetch(url, {
      method: method || 'GET',
      credentials: 'same-origin',
      headers: {'Content-Type': 'application/json', Accept: 'application/json'},
      body: body === undefined ? undefined : JSON.stringify(body)
    });
    if (response.status === 401) {
      location.href = '/login?returnTo=' + encodeURIComponent(location.pathname);
      throw Error('Vui lòng đăng nhập để tiếp tục.');
    }
    if (!response.ok) {
      let message = '';
      try { message = (await response.json()).message || ''; } catch (ignored) { /* not JSON */ }
      throw Error(message || 'Không thực hiện được. Vui lòng thử lại sau.');
    }
    return response.status === 204 ? null : response.json();
  }

  /** A small dialog with one text box. Resolves with the text, or null when the person cancelled. */
  function ask({title, intro, warning, label, initial, required, min, max, submit}) {
    return new Promise(resolve => {
      const dialog = document.createElement('dialog');
      dialog.className = 'stock-dialog warranty-dialog';
      dialog.innerHTML = `<form novalidate><h2>${esc(title)}</h2>${intro ? `<p class="muted">${esc(intro)}</p>` : ''}
        ${warning ? `<p class="notice error" role="note">${esc(warning)}</p>` : ''}
        <label>${esc(label)}<textarea name="text" rows="5" maxlength="${max}"></textarea></label>
        <p class="support-message error" role="alert" hidden></p>
        <p class="dialog-actions"><button type="submit" class="button compact" data-submit>${esc(submit)}</button><button type="button" class="button compact ghost" data-cancel>${'Hủy'}</button></p></form>`;
      document.body.appendChild(dialog);
      const form = dialog.querySelector('form'), error = dialog.querySelector('.support-message');
      form.elements.text.value = initial || '';
      let value = null;
      form.addEventListener('submit', event => {
        event.preventDefault();
        const text = form.elements.text.value.trim();
        if ((required || text) && text.length < min) {
          error.textContent = `Cần nhập ít nhất ${min} ký tự.`;
          error.hidden = false;
          return;
        }
        value = text;
        dialog.close();
      });
      dialog.querySelector('[data-cancel]').addEventListener('click', () => dialog.close());
      dialog.addEventListener('close', () => { dialog.remove(); resolve(value); });
      dialog.showModal();
      form.elements.text.focus();
    });
  }

  // ---- customer: requests already sent, under the list of vehicles ----
  async function renderMyClaims() {
    let panel = document.querySelector('#warrantyClaims');
    if (!panel) {
      panel = document.createElement('section');
      panel.id = 'warrantyClaims';
      panel.className = 'data-panel';
      panel.style.marginTop = '20px';
      document.querySelector('.work-main').appendChild(panel);
    }
    let rows;
    try { rows = await request('/api/v1/warranty/claims'); } catch (err) {
      // a failed load must not look like "no requests"
      panel.innerHTML = `<h2>${'Yêu cầu bảo hành đã gửi'}</h2><p class="notice error" role="alert">${esc(err.message)}</p>`;
      return;
    }
    if (!rows.length) {
      panel.innerHTML = `<h2>${'Yêu cầu bảo hành đã gửi'}</h2><p class="muted">${'Bạn chưa gửi yêu cầu bảo hành nào.'}</p>`;
      return;
    }
    const head = ['Xe', 'Số khung', 'Mô tả sự cố', 'Trạng thái', 'Ghi chú của cửa hàng', 'Ngày gửi'].map(name => `<th>${name}</th>`).join('');
    const body = rows.map(row => `<tr data-claim="${row.id}"><td>${esc(row.productName)}${row.colorName ? ' · ' + esc(row.colorName) : ''}</td><td>${esc(row.frameNumber)}</td><td>${esc(row.description)}</td><td data-field="claimStatus" data-status="${esc(row.status)}">${esc(statusText[row.status] || row.status)}${row.outOfWarranty ? ` <small>(${'ngoài bảo hành, có tính phí'})</small>` : ''}</td><td data-field="staffNote">${esc(row.staffNote || '-')}</td><td>${esc(day(row.createdAt))}</td></tr>`).join('');
    panel.innerHTML = `<h2>${'Yêu cầu bảo hành đã gửi'}</h2><div class="table-scroll"><table><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table></div>`;
  }

  function setupCustomer(table) {
    const lead = document.querySelector('.work-heading p:last-child');
    if (lead) lead.insertAdjacentHTML('afterend', `<p class="muted"><a href="/warranty">${'Tra cứu bảo hành bằng số khung'}</a></p>`);
    renderMyClaims();
    window.addEventListener('workspace:reload', renderMyClaims);
    table.addEventListener('click', async event => {
      const button = event.target.closest('[data-action="warranty-claim"]');
      if (!button) return;
      const text = await ask({
        title: 'Yêu cầu bảo hành',
        intro: button.dataset.frame ? `${button.dataset.name || ''} · ${'Số khung'} ${button.dataset.frame}` : '',
        // a bike out of warranty is repaired for a fee: say so before the customer sends the request, not after
        warning: button.dataset.coverage === 'EXPIRED' ? 'Xe đã hết bảo hành, việc sửa chữa sẽ có tính phí.' : '',
        label: 'Mô tả lỗi', required: true, min: 10, max: 2000, submit: 'Gửi yêu cầu'
      });
      if (text === null) return;
      button.disabled = true;
      try {
        await request('/api/v1/warranty/claims', 'POST', {unitId: Number(button.dataset.id), description: text});
        tell('Đã gửi yêu cầu bảo hành. Cửa hàng sẽ cập nhật trạng thái cho bạn.');
        window.dispatchEvent(new Event('workspace:reload'));
      } catch (err) {
        tell(err.message, true);
        button.disabled = false;
      }
    });
  }

  // ---- manager: move a request to its next step, with an optional note for the customer ----
  function setupManager(table) {
    table.addEventListener('click', async event => {
      const button = event.target.closest('[data-action="warranty-status"]');
      if (!button) return;
      const status = button.dataset.status;
      const rejected = status === 'REJECTED';
      // the box starts with the note the customer already sees, so a new text replaces it knowingly and an empty box clears it;
      // an empty status is the "edit note" button: the step stays as it is. The server only asks for a non-empty reason on a rejection.
      const note = await ask({
        title: status ? (statusText[status] || status) : 'Sửa ghi chú',
        intro: 'Ghi chú này khách sẽ thấy trong sổ bảo hành.',
        label: rejected ? 'Lý do từ chối' : 'Ghi chú cho khách (có thể để trống)',
        initial: button.dataset.note || '',
        required: rejected, min: 1, max: 3000, submit: 'Cập nhật'
      });
      if (note === null) return;
      button.disabled = true;
      try {
        await request(`/api/v1/manager/warranty-claims/${Number(button.dataset.id)}`, 'PATCH', status ? {status, staffNote: note} : {staffNote: note});
        tell('Đã cập nhật yêu cầu bảo hành.');
        window.dispatchEvent(new Event('workspace:reload'));
      } catch (err) {
        tell(err.message, true);
        button.disabled = false;
      }
    });
  }

  // ---- public lookup by frame number ----
  function renderLookup(target, finish) {
    finish();
    document.querySelector('#data-table')?.remove();
    target.innerHTML = `<section class="support-section"><p class="eyebrow">Bảo hành</p><h2>${'Tra cứu bảo hành theo số khung'}</h2>
      <p class="muted">${'Nhập số khung in trên xe hoặc phiếu bảo hành. Kết quả chỉ gồm tên xe và hạn bảo hành, không có thông tin chủ xe.'}</p>
      <form class="support-form" id="warrantyLookupForm" novalidate>
        <label class="wide">${'Số khung'}<input name="frame" required maxlength="40" autocomplete="off" placeholder="RLHPC1234MK000001"></label>
        <p class="wide"><button class="button compact" type="submit">${'Tra cứu'}</button></p>
      </form>
      <div id="warrantyLookupResult" class="warranty-result" role="status" aria-live="polite"></div></section>`;
    const form = target.querySelector('#warrantyLookupForm'), result = target.querySelector('#warrantyLookupResult');
    const lookup = async frame => {
      result.innerHTML = '';
      if (!frame.trim()) {
        result.innerHTML = `<p class="notice error">${'Vui lòng nhập số khung.'}</p>`;
        return;
      }
      history.replaceState(null, '', '/warranty?frame=' + encodeURIComponent(frame.trim()));
      try {
        const data = await request('/api/v1/warranty/lookup?frame=' + encodeURIComponent(frame.trim()));
        const line = (name, value) => `<p><span>${name}</span><strong>${value}</strong></p>`;
        result.innerHTML = `<article class="commerce-card warranty-card" data-coverage="${esc(data.coverage)}"><h3 data-field="productName">${esc(data.productName)}${data.colorName ? ' · ' + esc(data.colorName) : ''}</h3>
          ${line('Số khung', `<span data-field="frameNumber">${esc(data.frameNumber)}</span>`)}
          ${line('Thời hạn bảo hành', data.warrantyMonths ? (data.warrantyMonths === 1 ? '1 tháng' : `${data.warrantyMonths} tháng`) : '-')}
          ${line('Bắt đầu từ', `<span data-field="warrantyStartsAt">${esc(day(data.warrantyStartsAt))}</span>`)}
          ${line('Bảo hành đến', `<span data-field="warrantyEndsAt" data-iso="${esc(data.warrantyEndsAt || '')}">${esc(day(data.warrantyEndsAt))}</span>`)}
          ${line('Tình trạng', `<span data-field="coverage">${esc(coverageText[data.coverage] || data.coverage)}</span>`)}</article>`;
      } catch (err) {
        result.innerHTML = `<p class="notice error" role="alert">${esc(err.message)}</p>`;
      }
    };
    form.addEventListener('submit', event => {
      event.preventDefault();
      lookup(form.elements.frame.value);
    });
    const preset = new URLSearchParams(location.search).get('frame');
    if (preset) {
      form.elements.frame.value = preset;
      lookup(preset);
    }
  }

  const table = document.querySelector('#data-table');
  if (table && page === '/customer/warranty') setupCustomer(table);
  if (table && page === '/manager/warranty') setupManager(table);
  return {renderLookup};
})();
