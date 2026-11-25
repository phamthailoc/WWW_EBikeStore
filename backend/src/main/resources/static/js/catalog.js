// The admin catalogue page (/admin/catalog): bikes and categories, each with search, add, edit and delete.
// The rules live on the server (CatalogRules); this script only shows the lists and the forms and prints the server's sentences next to the form.
// The generic workspace script draws nothing here (the page has no data-api); it only keeps the menu and the sign-out button working.
(() => {
  const body = document.body;
  if (body.dataset.mode !== 'catalog') return;
  const API = '/api/v1/admin/catalog';
  const $ = selector => document.querySelector(selector);
  const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));
  const money = value => value == null ? '-' : new Intl.NumberFormat('vi-VN').format(Number(value)) + ' ₫';
  const VEHICLE = {E_SCOOTER: 'Xe máy điện', E_BIKE: 'Xe đạp điện', E_MOTORBIKE: 'Mô tô điện', BATTERY: 'Pin', ACCESSORY: 'Phụ kiện'};
  const BATTERY = {LEAD_ACID: 'Chì - axit', LITHIUM_ION: 'Lithium-ion', LFP: 'LFP'};

  const notice = $('#notice');
  const loading = $('#loading');
  const empty = $('#empty');
  const table = $('#data-table');
  const filters = $('#filters');
  const panelTitle = $('#panel-title');
  const actions = document.querySelector('#data-panel .product-action');
  let tab = 'products';
  let categories = [];

  const tell = (message, isError = false) => { notice.textContent = message; notice.hidden = false; notice.classList.toggle('error', isError); };
  const clearNotice = () => { notice.hidden = true; };

  async function request(url, method = 'GET', data) {
    const response = await fetch(url, {
      method, credentials: 'same-origin',
      headers: {Accept: 'application/json', ...(data === undefined ? {} : {'Content-Type': 'application/json'})},
      body: data === undefined ? undefined : JSON.stringify(data)
    });
    if (response.status === 401) {
      location.href = '/login?returnTo=' + encodeURIComponent(location.pathname);
      throw new Error('Vui lòng đăng nhập để tiếp tục.');
    }
    if (!response.ok) {
      let message = '';
      try { const json = await response.json(); message = json && json.message ? String(json.message) : ''; } catch (ignored) { /* not JSON */ }
      if (response.status === 403) throw new Error('Bạn không có quyền thực hiện thao tác này.');
      throw new Error(message || 'Không thể thực hiện thao tác.');
    }
    return response.status === 204 ? null : response.json();
  }

  function drawChrome() {
    actions.innerHTML = `
      <div role="tablist" class="product-action">
        <button type="button" role="tab" class="button compact ${tab === 'products' ? '' : 'ghost'}" data-tab="products" aria-selected="${tab === 'products'}">Xe</button>
        <button type="button" role="tab" class="button compact ${tab === 'categories' ? '' : 'ghost'}" data-tab="categories" aria-selected="${tab === 'categories'}">Loại sản phẩm</button>
        <button type="button" class="button compact" id="catalogAdd">${tab === 'products' ? '+ Thêm xe' : '+ Thêm loại'}</button>
      </div>`;
    actions.querySelectorAll('[data-tab]').forEach(button => button.addEventListener('click', () => { tab = button.dataset.tab; clearNotice(); load(); }));
    $('#catalogAdd').addEventListener('click', () => openForm(null));
    panelTitle.textContent = tab === 'products' ? 'Danh sách xe' : 'Danh sách loại sản phẩm';
    filters.hidden = false;
    filters.innerHTML = tab === 'products'
      ? `<form id="catalogFilter" class="filter-form" role="search" style="display:flex;gap:12px;flex-wrap:wrap;align-items:end;margin-bottom:16px">
           <label>Tìm kiếm<input name="search" placeholder="Tên hoặc đường dẫn" autocomplete="off"></label>
           <label>Loại<select name="categoryId"><option value="">Tất cả loại</option>${categories.map(c => `<option value="${c.id}">${esc(c.name)}</option>`).join('')}</select></label>
           <button class="button compact" type="submit">Lọc</button></form>`
      : `<form id="catalogFilter" class="filter-form" role="search" style="display:flex;gap:12px;flex-wrap:wrap;align-items:end;margin-bottom:16px">
           <label>Tìm kiếm<input name="search" placeholder="Tên hoặc đường dẫn" autocomplete="off"></label>
           <button class="button compact" type="submit">Lọc</button></form>`;
    $('#catalogFilter').addEventListener('submit', event => { event.preventDefault(); load(); });
  }

  function currentQuery() {
    const form = $('#catalogFilter');
    const params = new URLSearchParams();
    if (form) {
      const data = new FormData(form);
      if (data.get('search')) params.set('search', String(data.get('search')).trim());
      if (data.get('categoryId')) params.set('categoryId', String(data.get('categoryId')));
    }
    const text = params.toString();
    return text ? '?' + text : '';
  }

  async function load() {
    const keepSearch = $('#catalogFilter') ? new FormData($('#catalogFilter')) : null;
    if (!categories.length) {
      try { categories = await request(API + '/categories'); } catch (failure) { tell(failure.message, true); }
    }
    drawChrome();
    if (keepSearch) {
      const form = $('#catalogFilter');
      if (keepSearch.get('search') != null && form.elements.search) form.elements.search.value = keepSearch.get('search');
    }
    loading.hidden = false; empty.hidden = true; table.hidden = true;
    try {
      const rows = await request(API + (tab === 'products' ? '/products' : '/categories') + currentQuery());
      loading.hidden = true;
      table.hidden = rows.length === 0;
      empty.hidden = rows.length > 0;
      empty.textContent = 'Không có kết quả phù hợp.';
      if (tab === 'products') drawProducts(rows); else drawCategories(rows);
    } catch (failure) {
      loading.hidden = true;
      tell(failure.message, true);
    }
  }

  function deleteButton(row, label) {
    return row.canDelete
      ? `<button type="button" class="table-action" data-do="delete" data-id="${row.id}">Xóa</button>`
      : `<button type="button" class="table-action" disabled title="${esc(label)}">Xóa</button>`;
  }

  function drawProducts(rows) {
    table.querySelector('thead').innerHTML = '<tr><th>Mã</th><th>Tên</th><th>Loại</th><th>Giá bán</th><th>Tồn kho</th><th>Trạng thái</th><th>Đơn hàng</th><th>Thao tác</th></tr>';
    table.querySelector('tbody').innerHTML = rows.map(row => `<tr data-id="${row.id}">
      <td data-field="id">${row.id}</td><td>${esc(row.name)}<br><small>${esc(row.slug)}</small></td><td>${esc(row.categoryName)}</td>
      <td data-field="price">${money(row.discountPrice ?? row.price)}${row.discountPrice != null ? `<br><small><s>${money(row.price)}</s></small>` : ''}</td>
      <td>${row.stockQuantity}</td><td>${row.active ? 'Đang bán' : 'Ngừng bán'}${row.featured ? ' · Nổi bật' : ''}</td><td>${row.orderCount}</td>
      <td><button type="button" class="table-action" data-do="edit" data-id="${row.id}">Sửa</button>
      ${deleteButton(row, 'Xe đã có trong đơn hàng hoặc lịch lái thử nên không thể xóa; hãy ngừng bán.')}</td></tr>`).join('');
  }

  function drawCategories(rows) {
    table.querySelector('thead').innerHTML = '<tr><th>Mã</th><th>Tên</th><th>Đường dẫn</th><th>Số xe</th><th>Trạng thái</th><th>Thao tác</th></tr>';
    table.querySelector('tbody').innerHTML = rows.map(row => `<tr data-id="${row.id}">
      <td data-field="id">${row.id}</td><td>${esc(row.name)}</td><td>${esc(row.slug)}</td><td>${row.productCount}</td><td>${row.active ? 'Đang dùng' : 'Ẩn'}</td>
      <td><button type="button" class="table-action" data-do="edit" data-id="${row.id}">Sửa</button>
      ${deleteButton(row, 'Loại còn sản phẩm nên không thể xóa.')}</td></tr>`).join('');
  }

  table.addEventListener('click', async event => {
    const button = event.target.closest('[data-do]');
    if (!button || button.disabled) return;
    const id = button.dataset.id;
    clearNotice();
    try {
      if (button.dataset.do === 'edit') {
        openForm(await request(`${API}/${tab === 'products' ? 'products' : 'categories'}/${id}`));
      } else if (button.dataset.do === 'delete') {
        const name = button.closest('tr').children[1].textContent.trim();
        if (!confirm(`Xóa "${name}"? Thao tác này không thể hoàn tác.`)) return;
        await request(`${API}/${tab === 'products' ? 'products' : 'categories'}/${id}`, 'DELETE');
        if (tab === 'categories') categories = [];
        tell('Đã xóa.');
        load();
      }
    } catch (failure) { tell(failure.message, true); }
  });

  // ---- forms (native dialog) ----
  const dialog = document.createElement('dialog');
  dialog.id = 'catalogDialog';
  dialog.setAttribute('aria-labelledby', 'catalogDialogTitle');
  dialog.style.cssText = 'max-width:min(720px,94vw);width:100%;border:0;border-radius:18px;padding:28px;background:var(--surface);color:var(--ink);box-shadow:var(--shadow)';
  document.body.append(dialog);

  const field = (label, control, help) => `<label>${label}${control}${help ? `<small class="help">${help}</small>` : ''}</label>`;
  const input = (name, value, attrs = '') => `<input name="${name}" value="${esc(value)}" ${attrs}>`;
  const number = (name, value, attrs = '') => `<input name="${name}" type="number" step="any" min="0" value="${esc(value)}" ${attrs}>`;
  const select = (name, options, current, blank) => `<select name="${name}">${blank ? `<option value="">${blank}</option>` : ''}${Object.entries(options).map(([key, text]) => `<option value="${key}" ${key === current ? 'selected' : ''}>${esc(text)}</option>`).join('')}</select>`;

  function openForm(item) {
    const isProduct = tab === 'products';
    const editing = item != null;
    const title = (editing ? 'Sửa ' : 'Thêm ') + (isProduct ? 'xe' : 'loại sản phẩm');
    const categoryOptions = Object.fromEntries(categories.map(c => [String(c.id), c.name]));
    const fields = isProduct ? `
      ${field('Tên xe *', input('name', item?.name, 'required maxlength="255"'))}
      ${field('Đường dẫn', input('slug', item?.slug, 'maxlength="255" pattern="[a-z0-9]+(-[a-z0-9]+)*"'), 'Để trống để tạo từ tên. Chỉ chữ thường không dấu, số và dấu gạch ngang.')}
      ${field('Loại sản phẩm *', select('categoryId', categoryOptions, String(item?.categoryId ?? ''), 'Chọn loại').replace('<select', '<select required'))}
      <div class="profile-grid">
        ${field('Giá bán (₫) *', number('price', item?.price, 'required min="1" max="99999999"'))}
        ${field('Giá khuyến mãi (₫)', number('discountPrice', item?.discountPrice, 'min="1"'), 'Để trống nếu không giảm giá.')}
      </div>
      ${field('Mô tả', `<textarea name="description" rows="4" maxlength="5000">${esc(item?.description)}</textarea>`)}
      <div class="profile-grid">
        ${field('Thương hiệu', input('brand', item?.brand, 'maxlength="100"'))}
        ${field('Mã mẫu', input('modelCode', item?.modelCode, 'maxlength="100"'))}
        ${field('Loại xe', select('vehicleType', VEHICLE, item?.vehicleType ?? 'E_SCOOTER'))}
        ${field('Loại pin', select('batteryType', BATTERY, item?.batteryType, 'Không rõ'))}
        ${field('Dung lượng pin (Ah)', number('batteryCapacityAh', item?.batteryCapacityAh))}
        ${field('Điện áp pin (V)', number('batteryVoltageV', item?.batteryVoltageV))}
        ${field('Tốc độ tối đa (km/h)', number('maxSpeedKmh', item?.maxSpeedKmh))}
        ${field('Quãng đường tối đa (km)', number('maxRangeKm', item?.maxRangeKm))}
        ${field('Công suất động cơ (W)', number('motorPowerWatts', item?.motorPowerWatts, 'step="1"'))}
        ${field('Bảo hành (tháng)', number('warrantyMonths', item?.warrantyMonths, 'step="1" max="120"'))}
      </div>
      <label class="check-line"><input type="checkbox" name="active" ${item == null || item.active ? 'checked' : ''}> Đang bán</label>
      <label class="check-line"><input type="checkbox" name="featured" ${item?.featured ? 'checked' : ''}> Hiển thị nổi bật</label>
      ${editing ? `<p class="help">Tồn kho hiện tại: <b>${item.stockQuantity}</b>. Tồn kho chỉ đổi qua phiếu nhập kho và đơn hàng.</p>` : '<p class="help">Tồn kho bắt đầu từ 0; thêm hàng bằng phiếu nhập kho.</p>'}`
      : `
      ${field('Tên loại *', input('name', item?.name, 'required maxlength="100"'))}
      ${field('Đường dẫn', input('slug', item?.slug, 'maxlength="100" pattern="[a-z0-9]+(-[a-z0-9]+)*"'), 'Để trống để tạo từ tên.')}
      ${field('Mô tả', `<textarea name="description" rows="3" maxlength="5000">${esc(item?.description)}</textarea>`)}
      <label class="check-line"><input type="checkbox" name="active" ${item == null || item.active ? 'checked' : ''}> Đang dùng</label>`;
    dialog.innerHTML = `<form method="dialog" id="catalogForm" class="flow-form" style="max-width:none">
      <h2 id="catalogDialogTitle" style="margin:0 0 8px">${title}</h2>${fields}
      <p class="error" id="catalogError" role="alert"></p>
      <div class="product-action"><button type="submit" class="button">Lưu</button><button type="button" class="button ghost" id="catalogCancel">Hủy</button></div></form>`;
    $('#catalogCancel').addEventListener('click', () => dialog.close());
    $('#catalogForm').addEventListener('submit', async event => {
      event.preventDefault();
      const form = event.currentTarget;
      const submit = form.querySelector('button[type=submit]');
      submit.disabled = true;
      try {
        const payload = collect(form, isProduct);
        const base = `${API}/${isProduct ? 'products' : 'categories'}`;
        await request(editing ? `${base}/${item.id}` : base, editing ? 'PUT' : 'POST', payload);
        if (!isProduct) categories = [];
        dialog.close();
        tell(editing ? 'Đã lưu thay đổi.' : 'Đã thêm mới.');
        load();
      } catch (failure) {
        $('#catalogError').textContent = failure.message;
        submit.disabled = false;
      }
    });
    dialog.showModal();
  }

  function collect(form, isProduct) {
    const data = new FormData(form);
    const text = name => { const value = String(data.get(name) ?? '').trim(); return value === '' ? null : value; };
    const num = name => { const value = text(name); return value == null ? null : Number(value); };
    if (!isProduct) return {name: text('name'), slug: text('slug'), description: text('description'), active: form.elements.active.checked};
    return {
      name: text('name'), slug: text('slug'), description: text('description'),
      categoryId: num('categoryId'), price: num('price'), discountPrice: num('discountPrice'),
      featured: form.elements.featured.checked, active: form.elements.active.checked,
      brand: text('brand'), modelCode: text('modelCode'), vehicleType: text('vehicleType'), batteryType: text('batteryType'),
      batteryCapacityAh: num('batteryCapacityAh'), batteryVoltageV: num('batteryVoltageV'), maxSpeedKmh: num('maxSpeedKmh'),
      maxRangeKm: num('maxRangeKm'), motorPowerWatts: num('motorPowerWatts'), warrantyMonths: num('warrantyMonths')
    };
  }

  dialog.addEventListener('click', event => { if (event.target === dialog) dialog.close(); });
  load();
})();
