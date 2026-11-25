// V26: the stock receipts page (/manager/stock-receipts): the list of goods-received notes, the form that writes a new one and the detail of one.
// The generic workspace script draws nothing here (the page has no data-api); it only keeps the menu and the sign-out button working.
(() => {
  const body = document.body;
  if (body.dataset.mode !== 'stockreceipt') return;
  const canStock = body.dataset.canStock === 'true';
  const MAX_LINES = 30;
  const MAX_QUANTITY = 1000;
  const MAX_STOCK = 100000;
  const $ = selector => document.querySelector(selector);
  const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));
  const localeTag = () => 'vi-VN';
  const whenText = iso => { const d = new Date(iso); return isNaN(d) ? '-' : d.toLocaleString(localeTag()); };

  const notice = $('#notice');
  const loading = $('#loading');
  const empty = $('#empty');
  const table = $('#data-table');
  const filters = $('#filters');
  const panelTitle = $('#panel-title');
  const tell = (message, isError = false) => { notice.textContent = message; notice.hidden = false; notice.classList.toggle('error', isError); };
  const clearNotice = () => { notice.hidden = true; };

  async function request(url, method = 'GET', data) {
    const response = await fetch(url, {
      method, credentials: 'same-origin',
      headers: {Accept: 'application/json', ...(data === undefined ? {} : {'Content-Type': 'application/json'})},
      body: data === undefined ? undefined : JSON.stringify(data)
    });
    if (response.status === 401) {
      location.href = '/login?returnTo=' + encodeURIComponent(location.pathname + location.search);
      throw new Error('Vui lòng đăng nhập để tiếp tục.');
    }
    if (!response.ok) {
      let message = '';
      try { const json = await response.json(); message = json && json.message ? String(json.message) : ''; } catch (ignored) { /* not JSON */ }
      if (response.status === 403) throw new Error('Bạn không có quyền thực hiện thao tác này.');
      throw new Error(message || (method === 'GET' ? 'Không tải được dữ liệu.' : 'Không thể lưu thay đổi.'));
    }
    return response.status === 204 ? null : response.json();
  }

  panelTitle.textContent = 'Phiếu nhập kho';
  const actions = $('#data-panel .product-action');
  let newButton = null;
  if (canStock && actions) {
    newButton = document.createElement('button');
    newButton.type = 'button';
    newButton.id = 'newReceipt';
    newButton.className = 'button compact';
    newButton.textContent = '+ Tạo phiếu nhập';
    actions.insertBefore(newButton, actions.firstChild);
  }

  // ---- the list ------------------------------------------------------------------------
  let rows = [];
  let hasMore = false;
  let searchText = new URLSearchParams(location.search).get('q') || '';
  let seq = 0;
  const more = document.createElement('p');
  more.className = 'receipt-more';
  more.hidden = true;
  more.innerHTML = `<button type="button" class="button compact ghost" id="receiptMore">${'Xem thêm'}</button>`;
  table.closest('.table-scroll').insertAdjacentElement('afterend', more);

  function setupFilters() {
    filters.innerHTML = `<label>${'Tìm kiếm'}<input type="search" name="q" maxlength="150" autocomplete="off" placeholder="${'Nhà cung cấp hoặc số phiếu (PN-000012)'}" value="${esc(searchText)}"></label><button type="button" class="button compact ghost" id="clearReceiptSearch">${'Xóa bộ lọc'}</button>`;
    filters.hidden = false;
    const input = filters.querySelector('input');
    let timer;
    input.addEventListener('input', () => { clearTimeout(timer); timer = setTimeout(() => { searchText = input.value.trim(); load(true); }, 300); });
    filters.querySelector('#clearReceiptSearch').addEventListener('click', () => { input.value = ''; searchText = ''; load(true); });
  }

  function render() {
    more.hidden = !hasMore;
    if (!rows.length) {
      table.hidden = true;
      empty.textContent = searchText ? 'Không có phiếu nhập nào khớp.' : 'Chưa có phiếu nhập kho nào. Khi hàng về, bấm "Tạo phiếu nhập" để ghi nhận và cộng tồn kho.';
      empty.hidden = false;
      return;
    }
    empty.hidden = true;
    table.hidden = false;
    table.querySelector('thead').innerHTML = `<tr><th>${'Số phiếu'}</th><th>${'Nhà cung cấp'}</th><th>${'Số dòng'}</th><th>${'Tổng số xe'}</th><th>${'Người nhập'}</th><th>${'Thời gian'}</th><th>${'Thao tác'}</th></tr>`;
    table.querySelector('tbody').innerHTML = rows.map(row => `<tr>
      <td data-field="number"><strong>${esc(row.number)}</strong></td>
      <td data-field="supplierName">${esc(row.supplierName)}</td>
      <td data-field="lineCount">${row.lineCount}</td>
      <td data-field="totalQuantity">${row.totalQuantity}</td>
      <td data-field="createdByName">${row.createdByName ? esc(row.createdByName) : '-'}</td>
      <td data-field="createdAt">${esc(whenText(row.createdAt))}</td>
      <td><div class="product-action"><button type="button" class="table-action" data-receipt="${row.id}">${'Chi tiết'}</button></div></td></tr>`).join('');
  }

  async function load(reset) {
    if (!canStock) return;
    const mine = ++seq;
    if (reset) { rows = []; hasMore = false; }
    loading.hidden = false;
    clearNotice();
    const query = new URLSearchParams();
    if (searchText) query.set('q', searchText);
    if (!reset && rows.length) query.set('beforeId', rows[rows.length - 1].id);
    try {
      const page = await request(`/api/v1/manager/stock-receipts?${query}`);
      if (mine !== seq) return;
      rows = rows.concat(page.items || []);
      hasMore = !!page.hasMore;
      render();
    } catch (error) {
      if (mine !== seq) return;
      tell(error.message, true);
      if (!rows.length) { table.hidden = true; empty.hidden = true; more.hidden = true; }
    } finally {
      if (mine === seq) loading.hidden = true;
    }
  }

  // ---- the detail of one receipt -----------------------------------------------------------
  function showDetail(id) {
    const row = rows.find(item => item.id === id);
    if (!row) return;
    const dialog = document.createElement('dialog');
    dialog.className = 'stock-dialog receipt-dialog';
    dialog.innerHTML = `
      <h2>${esc(row.number)}</h2>
      <p class="muted">${esc(row.supplierName)} · ${esc(whenText(row.createdAt))}${row.createdByName ? ' · ' + esc(row.createdByName) : ''}</p>
      ${row.note ? `<p class="receipt-note">${esc(row.note)}</p>` : ''}
      <div class="table-scroll"><table class="stock-table"><thead><tr><th>${'Xe'}</th><th>${'Màu'}</th><th>${'Số lượng'}</th></tr></thead><tbody>
        ${row.lines.map(line => `<tr><td>${esc(line.productName)}</td><td>${line.colorName ? esc(line.colorName) : '-'}</td><td>${line.quantity}</td></tr>`).join('')}
        <tr class="receipt-total"><td colspan="2"><strong>${'Tổng'}</strong></td><td><strong>${row.totalQuantity}</strong></td></tr>
      </tbody></table></div>
      <p class="muted">${'Phiếu đã lưu không sửa được. Nếu nhập nhầm, hãy chỉnh tồn kho ở trang Kho hàng và ghi rõ lý do.'}</p>
      <p class="dialog-actions"><button type="button" class="button compact" data-close>${'Đóng'}</button></p>`;
    document.body.appendChild(dialog);
    dialog.addEventListener('click', event => { if (event.target.closest('[data-close]')) dialog.close(); });
    dialog.addEventListener('close', () => dialog.remove());
    dialog.showModal();
  }

  // ---- the form of a new receipt ------------------------------------------------------------
  function openForm() {
    const dialog = document.createElement('dialog');
    dialog.className = 'stock-dialog receipt-dialog receipt-form';
    dialog.innerHTML = `
      <form novalidate>
        <h2>${'Tạo phiếu nhập kho'}</h2>
        <p class="muted">${'Mỗi dòng là một màu xe và số lượng nhập về. Lưu phiếu sẽ cộng tồn kho ngay.'}</p>
        <div class="dialog-error" data-error role="alert" hidden></div>
        <label>${'Nhà cung cấp'} <span class="required">*</span><input type="text" name="supplier" maxlength="150" autocomplete="off" required></label>
        <label>${'Ghi chú'}<textarea name="note" rows="2" maxlength="500" placeholder="${'Ví dụ: số chứng từ, đợt hàng, tình trạng thùng hàng'}"></textarea></label>
        <p class="muted" data-loading-options>${'Đang tải danh sách xe…'}</p>
        <div class="table-scroll"><table class="stock-table receipt-lines" hidden><thead><tr><th>${'Xe'}</th><th>${'Màu'}</th><th>${'Số lượng'}</th><th></th></tr></thead><tbody></tbody></table></div>
        <p class="receipt-lines-actions"><button type="button" class="button compact ghost" data-add disabled>${'+ Thêm dòng'}</button> <span class="muted" data-sum></span></p>
        <p class="dialog-actions"><button type="submit" class="button compact" data-submit disabled>${'Lưu phiếu nhập'}</button><button type="button" class="button compact ghost" data-cancel>${'Hủy'}</button></p>
      </form>`;
    document.body.appendChild(dialog);
    const form = dialog.querySelector('form');
    const tbody = dialog.querySelector('tbody');
    const errorBox = dialog.querySelector('[data-error]');
    const addButton = dialog.querySelector('[data-add]');
    const submitButton = dialog.querySelector('[data-submit]');
    let products = [];
    let busy = false;

    const showError = message => { errorBox.textContent = message; errorBox.hidden = !message; if (message) errorBox.scrollIntoView({block: 'nearest'}); };
    const colourText = c => `${c.colorName} (${'tồn'} ${c.stock})${c.active ? '' : ' · ' + 'ngừng bán'}`;
    const productText = p => `${p.name}${p.active ? '' : ' · ' + 'ngừng bán'}`;

    function updateSum() {
      const lines = [...tbody.querySelectorAll('tr')];
      const total = lines.reduce((sum, tr) => { const q = Number(tr.querySelector('[data-qty]').value); return sum + (Number.isInteger(q) && q > 0 ? q : 0); }, 0);
      dialog.querySelector('[data-sum]').textContent = total > 0 ? `${lines.length} dòng · ${total} xe` : '';
      addButton.disabled = lines.length >= MAX_LINES;
    }

    function addLine() {
      if (tbody.children.length >= MAX_LINES) return;
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td data-label="${'Xe'}"><select data-product aria-label="${'Xe'}"><option value="">${'Chọn xe…'}</option>${products.map(p => `<option value="${p.id}">${esc(productText(p))}</option>`).join('')}</select></td>
        <td data-label="${'Màu'}"><select data-colour aria-label="${'Màu'}" disabled><option value="">-</option></select></td>
        <td data-label="${'Số lượng'}"><input type="number" data-qty min="1" max="${MAX_QUANTITY}" step="1" inputmode="numeric" aria-label="${'Số lượng'}"></td>
        <td><button type="button" class="table-action" data-remove aria-label="${'Xóa dòng'}">✕</button></td>`;
      tbody.appendChild(tr);
      dialog.querySelector('.receipt-lines').hidden = false;
      updateSum();
      return tr;
    }

    function fillColours(tr) {
      const product = products.find(p => String(p.id) === tr.querySelector('[data-product]').value);
      const select = tr.querySelector('[data-colour]');
      if (!product) { select.innerHTML = '<option value="">-</option>'; select.disabled = true; return; }
      if (!product.hasActiveColours) {
        select.innerHTML = `<option value="">${'Không chia màu'}</option>`;
        select.disabled = true;
        return;
      }
      const colours = [...product.colours].sort((a, b) => Number(b.active) - Number(a.active));
      select.innerHTML = `<option value="">${'Chọn màu…'}</option>` + colours.map(c => `<option value="${c.id}">${esc(colourText(c))}</option>`).join('');
      select.disabled = false;
    }

    // the same checks as the server's, so the person is told which line before anything is sent
    function collect() {
      const supplier = form.elements.supplier.value.trim().replace(/\s{2,}/g, ' ');
      if (!supplier) throw new Error('Vui lòng nhập nhà cung cấp.');
      const lines = [];
      const seen = new Set();
      [...tbody.querySelectorAll('tr')].forEach((tr, index) => {
        const n = index + 1;
        const productId = tr.querySelector('[data-product]').value;
        if (!productId) throw new Error(`Dòng ${n}: vui lòng chọn xe.`);
        const product = products.find(p => String(p.id) === productId);
        const colourId = tr.querySelector('[data-colour]').value;
        if (product && product.hasActiveColours && !colourId) throw new Error(`Dòng ${n}: xe này bán theo màu, hãy chọn màu nhận.`);
        const raw = tr.querySelector('[data-qty]').value.trim();
        const quantity = Number(raw);
        if (!raw || !Number.isInteger(quantity) || quantity <= 0) throw new Error(`Dòng ${n}: số lượng phải là số nguyên lớn hơn 0.`);
        if (quantity > MAX_QUANTITY) throw new Error(`Dòng ${n}: mỗi dòng nhập tối đa ${MAX_QUANTITY} xe.`);
        const key = productId + ':' + (colourId || '');
        if (seen.has(key)) throw new Error(`Dòng ${n}: màu này đã có ở dòng trên, hãy gộp số lượng vào một dòng.`);
        seen.add(key);
        if (product) {
          const current = colourId ? (product.colours.find(c => String(c.id) === colourId) || {}).stock : product.stock;
          if ((current || 0) + quantity > MAX_STOCK) throw new Error(`Dòng ${n}: tồn kho sau khi nhập sẽ vượt ${MAX_STOCK} xe.`);
        }
        lines.push({productId: Number(productId), variantId: colourId ? Number(colourId) : null, quantity});
      });
      if (!lines.length) throw new Error('Phiếu nhập cần có ít nhất một dòng.');
      return {supplierName: supplier, note: form.elements.note.value.trim(), lines};
    }

    const isDirty = () => form.elements.supplier.value.trim() !== '' || form.elements.note.value.trim() !== ''
      || [...tbody.querySelectorAll('[data-qty]')].some(input => input.value !== '');
    const requestClose = () => { if (busy) return; if (!isDirty() || confirm('Bỏ phiếu đang nhập? Những gì đã điền sẽ mất.')) dialog.close(); };

    dialog.addEventListener('change', event => {
      if (event.target.matches('[data-product]')) fillColours(event.target.closest('tr'));
      showError('');
    });
    dialog.addEventListener('input', event => { if (event.target.matches('[data-qty]')) updateSum(); });
    dialog.addEventListener('click', event => {
      if (event.target.closest('[data-add]')) { const tr = addLine(); if (tr) tr.querySelector('[data-product]').focus(); }
      const remove = event.target.closest('[data-remove]');
      if (remove) {
        remove.closest('tr').remove();
        if (!tbody.children.length) addLine();
        updateSum();
      }
      if (event.target.closest('[data-cancel]')) requestClose();
    });
    // Escape asks first when something was typed; a half-written receipt is not thrown away by a stray key
    dialog.addEventListener('cancel', event => { event.preventDefault(); requestClose(); });
    dialog.addEventListener('close', () => dialog.remove());

    form.addEventListener('submit', async event => {
      event.preventDefault();
      if (busy) return;
      let payload;
      try { payload = collect(); } catch (error) { showError(error.message); return; }
      busy = true;
      submitButton.disabled = true;
      submitButton.textContent = 'Đang lưu…';
      showError('');
      try {
        const saved = await request('/api/v1/manager/stock-receipts', 'POST', payload);
        dialog.close();
        tell(`Đã lưu phiếu ${saved.number}: nhập thêm ${saved.totalQuantity} xe.`);
        await load(true);
      } catch (error) {
        showError(error.message);
      } finally {
        busy = false;
        submitButton.disabled = false;
        submitButton.textContent = 'Lưu phiếu nhập';
      }
    });

    dialog.showModal();
    form.elements.supplier.focus();
    request('/api/v1/manager/stock-receipts/options').then(list => {
      products = list || [];
      dialog.querySelector('[data-loading-options]').hidden = true;
      if (!products.length) { showError('Chưa có xe nào trong danh mục để nhập kho.'); return; }
      addLine();
      submitButton.disabled = false;
    }).catch(error => {
      dialog.querySelector('[data-loading-options]').hidden = true;
      showError(error.message);
    });
  }

  // ---- wiring ------------------------------------------------------------------------------
  document.addEventListener('DOMContentLoaded', () => {
    $('#refresh')?.addEventListener('click', () => load(true));
    if (!canStock) {
      loading.hidden = true;
      table.hidden = true;
      empty.textContent = 'Bạn không có quyền xem phiếu nhập kho. Hãy nhờ quản trị viên cấp quyền quản lý tồn kho.';
      empty.hidden = false;
      tell('Bạn không có quyền thực hiện thao tác này.', true);
      return;
    }
    newButton?.addEventListener('click', openForm);
    more.addEventListener('click', event => { if (event.target.closest('#receiptMore')) load(false); });
    table.addEventListener('click', event => {
      const button = event.target.closest('[data-receipt]');
      if (button) showDetail(Number(button.dataset.receipt));
    });
    setupFilters();
    load(true);
  });
})();
