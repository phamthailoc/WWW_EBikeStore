// Admin forms (V6): create account, change role, delete account, create/edit discount code.
// Replaces the old prompt() chains. It listens in the capture phase so these actions never reach the
// generic table handlers in workspace.js.
(() => {
  const page = document.body.dataset.page || '';
  const notice = document.querySelector('#notice');
  const tell = (message, error = false) => {
    notice.textContent = message;
    notice.hidden = false;
    notice.classList.toggle('error', error);
    notice.scrollIntoView({block: 'nearest'});
  };
  const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));
  const reload = () => window.dispatchEvent(new Event('workspace:reload'));

  async function api(url, method = 'GET', data) {
    const response = await fetch(url, {
      method,
      credentials: 'same-origin',
      headers: {Accept: 'application/json', 'Content-Type': 'application/json'},
      body: data === undefined ? undefined : JSON.stringify(data)
    });
    if (response.status === 401) {
      location.href = '/login?returnTo=' + encodeURIComponent(location.pathname);
      throw Error('Phiên đăng nhập đã hết hạn.');
    }
    if (!response.ok) {
      let message = '';
      try { message = (await response.json()).message || ''; } catch (ignored) { /* not JSON */ }
      throw Error(message || 'Không thể lưu thay đổi.');
    }
    if (response.status === 204) return null;
    try { return await response.json(); } catch (ignored) { return null; }
  }

  /** Shows a form in a modal. submit(form) does the work; a thrown error is shown inside the dialog. */
  function formDialog({title, intro, fields, submitLabel, submit}) {
    const dialog = document.createElement('dialog');
    dialog.className = 'admin-dialog';
    dialog.innerHTML = `
      <form class="admin-form">
        <h2>${esc(title)}</h2>
        ${intro ? `<p class="muted">${intro}</p>` : ''}
        <div class="admin-fields">${fields}</div>
        <p class="dialog-error" role="alert" hidden></p>
        <p class="dialog-actions">
          <button type="submit" class="button compact">${esc(submitLabel)}</button>
          <button type="button" class="button compact ghost" data-cancel>Hủy</button>
        </p>
      </form>`;
    document.body.appendChild(dialog);
    const form = dialog.querySelector('form');
    const error = dialog.querySelector('.dialog-error');
    dialog.querySelector('[data-cancel]').addEventListener('click', () => dialog.close());
    dialog.addEventListener('close', () => dialog.remove());
    form.addEventListener('submit', async event => {
      event.preventDefault();
      error.hidden = true;
      const button = form.querySelector('[type=submit]');
      button.disabled = true;
      try {
        await submit(form);
        dialog.close();
      } catch (failure) {
        error.textContent = failure.message;
        error.hidden = false;
      } finally {
        button.disabled = false;
      }
    });
    dialog.showModal();
    return dialog;
  }

  const field = (label, control) => `<label>${esc(label)}${control}</label>`;
  const options = (items, selected) => items.map(([value, text]) =>
    `<option value="${esc(value)}"${value === selected ? ' selected' : ''}>${esc(text)}</option>`).join('');

  // ---- accounts -----------------------------------------------------------------------
  async function assignableRoles() {
    const roles = await api('/api/v1/admin/roles');
    return roles.filter(r => r.role !== 'SUPER_ADMIN').map(r => [r.role, r.title || r.role]);
  }

  async function createAccount() {
    const roles = await assignableRoles();
    formDialog({
      title: 'Tạo tài khoản',
      fields: field('Họ và tên', '<input name="name" required maxlength="150">')
        + field('Email', '<input name="email" type="email" required maxlength="255">')
        + field('Tên đăng nhập (để trống sẽ lấy phần trước @ của email)', '<input name="username" maxlength="100">')
        + field('Mật khẩu tạm thời (ít nhất 6 ký tự)', '<input name="password" type="password" minlength="6" required autocomplete="new-password">')
        + field('Vai trò', `<select name="role">${options(roles, 'CUSTOMER')}</select>`),
      submitLabel: 'Tạo tài khoản',
      submit: async form => {
        await api('/api/v1/admin/accounts', 'POST', {
          name: form.name.value.trim(),
          email: form.email.value.trim(),
          username: form.username.value.trim() || null,
          password: form.password.value,
          role: form.role.value,
          status: 'ACTIVE',
          verified: true
        });
        tell('Đã tạo tài khoản.');
        reload();
      }
    });
  }

  async function changeRole(id) {
    const roles = await assignableRoles();
    formDialog({
      title: 'Đổi vai trò',
      intro: 'Vai trò quyết định các trang và thao tác tài khoản này được dùng.',
      fields: field('Vai trò mới', `<select name="role">${options(roles, 'CUSTOMER')}</select>`),
      submitLabel: 'Lưu vai trò',
      submit: async form => {
        await api(`/api/v1/admin/accounts/${id}/role`, 'PATCH', {role: form.role.value});
        tell('Đã đổi vai trò.');
        reload();
      }
    });
  }

  // Erasing an account cannot be undone, so the admin has to type its email before the button works.
  function deleteAccount(id, email) {
    formDialog({
      title: 'Xóa tài khoản',
      intro: 'Chỉ làm khi khách yêu cầu xóa. Họ tên, địa chỉ đã lưu, yêu thích, thông báo và <b>đánh giá</b> của tài khoản này sẽ bị xóa; tài khoản không đăng nhập được nữa và <b>không khôi phục được</b>. Đơn hàng cũ vẫn được giữ. Nếu chỉ muốn chặn tài khoản, hãy chọn Vô hiệu hóa.',
      fields: `<label>Gõ đúng email <b>${esc(email)}</b> để xác nhận<input name="confirmEmail" autocomplete="off" required></label>`,
      submitLabel: 'Xóa vĩnh viễn',
      submit: async form => {
        const typed = new FormData(form).get('confirmEmail').trim();
        if (typed.toLowerCase() !== String(email).toLowerCase()) throw Error('Email chưa khớp với tài khoản cần xóa.');
        await api(`/api/v1/admin/accounts/${id}?confirmEmail=${encodeURIComponent(typed)}`, 'DELETE');
        tell('Đã xóa tài khoản.');
        reload();
      }
    });
  }

  // ---- discount codes -----------------------------------------------------------------
  const toInput = iso => (iso || '').slice(0, 16);
  const toApi = value => (value ? value + ':00' : null);
  const statusOptions = [['WAITING', 'Chờ áp dụng'], ['LIVE', 'Đang chạy'], ['EXPIRED', 'Hết hạn'], ['DISABLED', 'Tắt']];

  function promotionDialog(promotion) {
    const editing = Boolean(promotion);
    const p = promotion || {discountType: 'PERCENTAGE', status: 'WAITING', usageLimit: 0};
    formDialog({
      title: editing ? `Sửa mã ${p.code}` : 'Tạo mã khuyến mãi',
      intro: 'Mỗi khách chỉ dùng một mã một lần; đơn bị hủy thì được dùng lại.',
      fields: field('Mã (chữ và số, không dấu cách)', `<input name="code" required maxlength="40" value="${esc(p.code || '')}"${editing ? ' disabled' : ''}>`)
        + field('Tên chương trình', `<input name="campaignName" required maxlength="150" value="${esc(p.campaignName || '')}">`)
        + field('Loại giảm', `<select name="discountType">${options([['PERCENTAGE', 'Theo phần trăm (%)'], ['FIXED_AMOUNT', 'Số tiền cố định (₫)']], p.discountType)}</select>`)
        + field('Giá trị giảm', `<input name="discountValue" type="number" min="1" step="any" required value="${esc(p.discountValue ?? '')}">`)
        + field('Giảm tối đa (₫, để 0 nếu không giới hạn)', `<input name="maxDiscountAmount" type="number" min="0" step="1000" value="${esc(p.maxDiscountAmount ?? 0)}">`)
        + field('Số lượt dùng tối đa (0 = không giới hạn)', `<input name="usageLimit" type="number" min="0" step="1" value="${esc(p.usageLimit ?? 0)}">`)
        + field('Bắt đầu', `<input name="startsAt" type="datetime-local" value="${esc(toInput(p.startsAt))}">`)
        + field('Kết thúc', `<input name="endsAt" type="datetime-local" value="${esc(toInput(p.endsAt))}">`)
        + field('Trạng thái', `<select name="status">${options(statusOptions, p.status)}</select>`),
      submitLabel: editing ? 'Lưu thay đổi' : 'Tạo mã',
      submit: async form => {
        const body = {
          campaignName: form.campaignName.value.trim(),
          discountType: form.discountType.value,
          discountValue: Number(form.discountValue.value),
          maxDiscountAmount: Number(form.maxDiscountAmount.value || 0),
          usageLimit: Number(form.usageLimit.value || 0),
          status: form.status.value,
          startsAt: toApi(form.startsAt.value),
          endsAt: toApi(form.endsAt.value)
        };
        if (editing) {
          await api(`/api/v1/admin/promotions/${p.id}`, 'PATCH', body);
          tell('Đã cập nhật mã khuyến mãi.');
        } else {
          await api('/api/v1/admin/promotions', 'POST', {...body, code: form.code.value.trim().toUpperCase()});
          tell('Đã tạo mã khuyến mãi.');
        }
        reload();
      }
    });
  }

  async function editPromotion(id) {
    const all = await api('/api/v1/admin/promotions');
    const promotion = all.find(item => String(item.id) === String(id));
    if (promotion) promotionDialog(promotion);
  }

  // ---- wiring -------------------------------------------------------------------------
  const handlers = {
    'role': id => changeRole(id),
    'delete-account': (id, button) => deleteAccount(id, button.dataset.email),
    'promotion': id => editPromotion(id)
  };

  document.addEventListener('click', event => {
    if (event.target.closest('#create-account')) {
      event.stopImmediatePropagation();
      createAccount().catch(error => tell(error.message, true));
      return;
    }
    if (event.target.closest('#addPromotion')) {
      event.stopImmediatePropagation();
      promotionDialog(null);
      return;
    }
    const button = event.target.closest('[data-action]');
    const handler = button && handlers[button.dataset.action];
    if (handler) {
      event.stopImmediatePropagation();
      Promise.resolve(handler(button.dataset.id, button)).catch(error => tell(error.message, true));
    }
  }, true);
})();
