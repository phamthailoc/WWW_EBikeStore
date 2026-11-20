// Customer profile extras: change password, saved addresses and the notification switch (V4).
(() => {
  const host = document.querySelector('#profileExtras');
  if (!host) return;
  const notice = document.querySelector('#notice');
  const tell = (message, error = false) => {
    notice.textContent = message;
    notice.hidden = false;
    notice.classList.toggle('error', error);
    notice.scrollIntoView({block: 'nearest'});
  };
  const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));

  async function api(url, method = 'GET', data) {
    const response = await fetch(url, {
      method,
      credentials: 'same-origin',
      headers: {Accept: 'application/json', 'Content-Type': 'application/json'},
      body: data === undefined ? undefined : JSON.stringify(data)
    });
    if (response.status === 401) {
      location.href = '/login?returnTo=' + encodeURIComponent(location.pathname);
      throw Error(t('Vui lòng đăng nhập để tiếp tục.'));
    }
    if (!response.ok) {
      let message = '';
      try { message = (await response.json()).message || ''; } catch (ignored) { /* not JSON */ }
      throw Error(message || t('Không thể lưu thay đổi.'));
    }
    if (response.status === 204) return null;
    try { return await response.json(); } catch (ignored) { return null; }
  }

  let userId = null;
  let addresses = [];
  let editingId = null;

  host.innerHTML = `
    <section class="data-panel profile-extra" id="passwordPanel">
      <p class="eyebrow">${t('BẢO MẬT')}</p><h2>${t('Đổi mật khẩu')}</h2>
      <form id="passwordForm" class="profile-grid" autocomplete="off">
        <label>${t('Mật khẩu hiện tại')}<input type="password" name="currentPassword" autocomplete="current-password" required></label>
        <label>${t('Mật khẩu mới (ít nhất 6 ký tự)')}<input type="password" name="newPassword" autocomplete="new-password" minlength="6" required></label>
        <label>${t('Nhập lại mật khẩu mới')}<input type="password" name="confirmPassword" autocomplete="new-password" minlength="6" required></label>
        <p><button class="button compact" type="submit">${t('Đổi mật khẩu')}</button></p>
      </form>
    </section>
    <section class="data-panel profile-extra" id="addressPanel">
      <p class="eyebrow">${t('GIAO HÀNG')}</p><h2>${t('Sổ địa chỉ')}</h2>
      <p class="muted">${t('Địa chỉ đã lưu được điền sẵn khi bạn chọn giao xe tận nhà. Lưu tối đa 10 địa chỉ.')}</p>
      <ul id="addressList" class="address-list"></ul>
      <form id="addressForm" class="profile-grid">
        <h3 id="addressFormTitle">${t('Thêm địa chỉ mới')}</h3>
        <label>${t('Tỉnh/Thành phố')}<select name="city" required></select></label>
        <label>${t('Địa chỉ cụ thể (số nhà, đường, phường/xã)')}<input name="street" minlength="10" maxlength="255" required placeholder="${t('123 Nguyễn Trãi, Phường Bến Thành')}"></label>
        <label class="inline-check"><input type="checkbox" name="isDefault"> ${t('Đặt làm địa chỉ mặc định')}</label>
        <p><button class="button compact" type="submit" id="addressSubmit">${t('Lưu địa chỉ')}</button>
        <button class="button compact ghost" type="button" id="addressCancel" hidden>${t('Hủy sửa')}</button></p>
      </form>
    </section>
    <section class="data-panel profile-extra" id="preferencePanel">
      <p class="eyebrow">${t('TÙY CHỌN')}</p><h2>${t('Giao diện và thông báo')}</h2>
      <label>${t('Ngôn ngữ / Language')}<select id="langChoice"><option value="vi">${t('Tiếng Việt')}</option><option value="en">English</option></select></label>
      <label>${t('Giao diện')}<select id="themeChoice"><option value="system">${t('Theo thiết bị')}</option><option value="light">${t('Sáng')}</option><option value="dark">${t('Tối')}</option></select></label>
      <label class="inline-check"><input type="checkbox" id="notificationsEnabled"> ${t('Nhận thông báo về đơn hàng và thanh toán')}</label>
    </section>`;

  // Customers can erase their own account (staff accounts are managed by an admin).
  if (document.body.dataset.role === 'customer') {
    host.insertAdjacentHTML('beforeend', `
    <section class="data-panel profile-extra" id="deletePanel">
      <p class="eyebrow">${t('TÀI KHOẢN')}</p><h2>${t('Xóa tài khoản')}</h2>
      <p class="muted">${t('Họ tên, địa chỉ đã lưu, yêu thích, thông báo và đánh giá của bạn sẽ bị xóa; bạn không đăng nhập lại được và')} <b>${t('không khôi phục được')}</b>${t('. Đơn hàng đã đặt vẫn được cửa hàng lưu theo quy định kế toán. Không xóa được khi còn đơn chưa hoàn tất.')}</p>
      <form id="deleteForm" class="profile-grid" autocomplete="off">
        <label>${t('Mật khẩu hiện tại')}<input type="password" name="password" autocomplete="current-password" required></label>
        <label>${t('Gõ lại email tài khoản để xác nhận')}<input type="email" name="confirmEmail" autocomplete="off" required></label>
        <p><button class="button compact" type="submit">${t('Xóa tài khoản của tôi')}</button></p>
      </form>
    </section>`);
    host.querySelector('#deleteForm').addEventListener('submit', async event => {
      event.preventDefault();
      if (!confirm(t('Xóa vĩnh viễn tài khoản của bạn?'))) return;
      const form = event.currentTarget;
      try {
        await api('/api/v1/auth/account', 'DELETE', Object.fromEntries(new FormData(form)));
        location.href = '/';
      } catch (error) {
        tell(error.message, true);
      }
    });
  }

  const addressForm = host.querySelector('#addressForm');
  const list = host.querySelector('#addressList');

  function renderAddresses() {
    list.innerHTML = addresses.length
      ? addresses.map(a => `<li data-id="${a.id}">
          <div><strong>${esc(a.city)}</strong>${a.isDefault ? ' <span class="badge">' + t('Mặc định') + '</span>' : ''}<br><span>${esc(a.street)}</span></div>
          <div class="row-actions">
            ${a.isDefault ? '' : '<button class="table-action" data-do="default" type="button">' + t('Đặt mặc định') + '</button>'}
            <button class="table-action" data-do="edit" type="button">${t('Sửa')}</button>
            <button class="table-action" data-do="delete" type="button">${t('Xóa')}</button>
          </div></li>`).join('')
      : '<li class="empty-line">' + t('Bạn chưa lưu địa chỉ nào.') + '</li>';
  }

  function resetAddressForm() {
    editingId = null;
    addressForm.reset();
    host.querySelector('#addressFormTitle').textContent = t('Thêm địa chỉ mới');
    host.querySelector('#addressCancel').hidden = true;
  }

  async function loadAddresses() {
    addresses = await api(`/api/v1/users/${userId}/addresses`);
    renderAddresses();
  }

  const body = form => ({
    addressType: 'SHIPPING',
    city: form.city.value,
    street: form.street.value.trim(),
    country: t('Việt Nam'),
    isDefault: form.isDefault.checked
  });

  host.querySelector('#passwordForm').addEventListener('submit', async event => {
    event.preventDefault();
    const form = event.currentTarget;
    if (form.newPassword.value !== form.confirmPassword.value) {
      tell(t('Mật khẩu nhập lại không khớp.'), true);
      return;
    }
    try {
      await api('/api/v1/auth/change-password', 'POST', {
        currentPassword: form.currentPassword.value,
        newPassword: form.newPassword.value,
        confirmPassword: form.confirmPassword.value
      });
      form.reset();
      tell(t('Đã đổi mật khẩu.'));
    } catch (error) {
      tell(error.message, true);
    }
  });

  addressForm.addEventListener('submit', async event => {
    event.preventDefault();
    try {
      const wasEditing = editingId !== null;
      if (wasEditing) {
        await api(`/api/v1/users/${userId}/addresses/${editingId}`, 'PUT', body(addressForm));
      } else {
        await api(`/api/v1/users/${userId}/addresses`, 'POST', body(addressForm));
      }
      resetAddressForm();
      await loadAddresses();
      tell(wasEditing ? t('Đã cập nhật địa chỉ.') : t('Đã lưu địa chỉ.'));
    } catch (error) {
      tell(error.message, true);
    }
  });

  host.querySelector('#addressCancel').addEventListener('click', resetAddressForm);

  list.addEventListener('click', async event => {
    const button = event.target.closest('[data-do]');
    if (!button) return;
    const id = Number(button.closest('li').dataset.id);
    const address = addresses.find(a => a.id === id);
    try {
      if (button.dataset.do === 'delete') {
        if (!confirm(t('Xóa địa chỉ này?'))) return;
        await api(`/api/v1/users/${userId}/addresses/${id}`, 'DELETE');
        if (editingId === id) resetAddressForm();
        await loadAddresses();
        tell(t('Đã xóa địa chỉ.'));
      } else if (button.dataset.do === 'default') {
        await api(`/api/v1/users/${userId}/addresses/${id}`, 'PUT', {
          addressType: address.addressType, city: address.city, street: address.street,
          country: address.country, isDefault: true
        });
        await loadAddresses();
        tell(t('Đã đặt làm địa chỉ mặc định.'));
      } else if (button.dataset.do === 'edit') {
        editingId = id;
        addressForm.city.value = address.city;
        addressForm.street.value = address.street;
        addressForm.isDefault.checked = Boolean(address.isDefault);
        host.querySelector('#addressFormTitle').textContent = t('Sửa địa chỉ');
        host.querySelector('#addressCancel').hidden = false;
        addressForm.street.focus();
      }
    } catch (error) {
      tell(error.message, true);
    }
  });

  // theme.js applies and saves the choice (also to this account); the select only mirrors it.
  // i18n.js stores the choice (cookie and account) and reloads this page in the new language.
  const langChoice = host.querySelector('#langChoice');
  if (window.ebikeLang) {
    langChoice.value = window.ebikeLang.get();
    langChoice.addEventListener('change', () => window.ebikeLang.set(langChoice.value));
  } else {
    langChoice.closest('label').hidden = true;
  }
  const themeChoice = host.querySelector('#themeChoice');
  if (window.ebikeTheme) {
    themeChoice.value = window.ebikeTheme.get();
    themeChoice.addEventListener('change', () => window.ebikeTheme.set(themeChoice.value));
    document.addEventListener('ebike-theme', event => { themeChoice.value = event.detail; });
  } else {
    themeChoice.closest('label').hidden = true;
  }

  host.querySelector('#notificationsEnabled').addEventListener('change', async event => {
    const checkbox = event.currentTarget;
    try {
      await api(`/api/v1/users/${userId}/preferences`, 'PUT', {notificationsEnabled: checkbox.checked});
      tell(checkbox.checked ? t('Đã bật thông báo.') : t('Đã tắt thông báo.'));
    } catch (error) {
      checkbox.checked = !checkbox.checked;
      tell(error.message, true);
    }
  });

  (async () => {
    try {
      const [profile, provinces] = await Promise.all([api('/api/v1/auth/profile'), fetch('/data/vietnam-provinces.json').then(r => r.json())]);
      userId = profile.userId;
      addressForm.city.innerHTML = '<option value="">' + t('Chọn Tỉnh/Thành phố') + '</option>'
        + provinces.map(p => `<option value="${esc(p)}">${esc(p)}</option>`).join('');
      const preference = await api(`/api/v1/users/${userId}/preferences`);
      host.querySelector('#notificationsEnabled').checked = preference.notificationsEnabled !== false;
      await loadAddresses();
    } catch (error) {
      tell(error.message, true);
    }
  })();
})();
