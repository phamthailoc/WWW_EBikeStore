// Per-colour stock editor for the manager and admin product tables (V17), and the stock history of a bike (V26).
// A product with colours sells from its colours, so its total is the sum of what each colour has left.
window.KineticStock = (() => {
  const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));

  /**
   * Opens the editor for a product detail (from /api/v1/products/{slug}).
   * save(url, method, body) sends the request and returns the parsed response; say(message, isError) shows a notice.
   * Resolves when the dialog is closed.
   */
  // Same thresholds as the product table: none left, or 3 and fewer.
  const tag = n => n <= 0 ? [t('Hết hàng'), 'out'] : n <= 3 ? [t('Sắp hết'), 'low'] : [t('Còn hàng'), 'ok'];
  const tagHtml = n => { const [text, kind] = tag(n); return `<span class="stock-tag ${kind}" data-tag>${text}</span>`; };

  function open(product, save, say) {
    return new Promise(resolve => {
      const dialog = document.createElement('dialog');
      dialog.className = 'stock-dialog';
      const colours = (product.variants || []).filter(v => v.active !== false);
      let total = colours.reduce((sum, v) => sum + (v.stockQuantity || 0), 0);
      dialog.innerHTML = `
        <form method="dialog">
          <h2>${t('Tồn kho theo màu')}</h2>
          <p class="muted">${esc(product.name)} · ${t('tổng')} <strong data-total>${total}</strong> ${t('xe')}</p>
          <table class="stock-table"><tbody>
            ${colours.map(v => `<tr data-variant="${v.id}">
              <td><span class="swatch" style="background:${esc(v.colorHex || '#ccc')}"></span> ${esc(v.colorName || v.variantName)}</td>
              <td><input type="number" min="0" step="1" value="${v.stockQuantity || 0}" aria-label="${t('Tồn kho')} ${esc(v.colorName || v.variantName)}"></td>
              <td data-tag-cell>${tagHtml(v.stockQuantity || 0)}</td>
              <td><button type="button" class="table-action" data-save>${t('Lưu')}</button></td></tr>`).join('')}
          </tbody></table>
          <label class="stock-note">${t('Lý do thay đổi (không bắt buộc)')}<input type="text" maxlength="500" data-note placeholder="${t('Ví dụ: kiểm kê kho, xe trưng bày, hàng lỗi')}"></label>
          <p class="muted stock-hint">${t('Hàng mới về hãy tạo phiếu nhập kho để có sổ đối soát. Sửa số ở đây dùng cho kiểm kê hoặc sửa nhầm.')}</p>
          <p class="dialog-actions"><button type="button" class="button compact ghost" data-history>${t('Lịch sử tồn kho')}</button><button class="button compact" value="close">${t('Đóng')}</button></p>
        </form>`;
      document.body.appendChild(dialog);

      dialog.addEventListener('click', async event => {
        if (event.target.closest('[data-history]')) {
          await history({id: product.id, name: product.name});
          return;
        }
        const button = event.target.closest('[data-save]');
        if (!button) return;
        const row = button.closest('tr');
        const input = row.querySelector('input');
        const quantity = Number(input.value);
        if (!Number.isInteger(quantity) || quantity < 0) {
          say(t('Số lượng tồn kho phải là số nguyên không âm.'), true);
          return;
        }
        const noteInput = dialog.querySelector('[data-note]');
        button.disabled = true;
        try {
          const summary = await save(`/api/v1/manager/products/${product.id}/variants/${row.dataset.variant}/stock`, 'PATCH', {stockQuantity: quantity, note: noteInput.value.trim()});
          row.querySelector('[data-tag-cell]').innerHTML = tagHtml(quantity);
          total = summary && summary.stockQuantity != null ? summary.stockQuantity : total;
          dialog.querySelector('[data-total]').textContent = total;
          noteInput.value = '';
          say(t('Đã cập nhật tồn kho.'));
        } catch (error) {
          say(error.message, true);
        } finally {
          button.disabled = false;
        }
      });
      dialog.addEventListener('close', () => {
        dialog.remove();
        resolve();
      });
      dialog.showModal();
    });
  }

  // ---- V26: the history of a bike's stock -------------------------------------------------
  const reasonText = {
    RECEIPT: t('Nhập kho'), SALE: t('Bán hàng'), CANCEL_RESTORE: t('Hoàn kho do hủy đơn'), ADJUSTMENT: t('Điều chỉnh'), OPENING: t('Tồn đầu kỳ')
  };
  const localeTag = () => document.documentElement.lang === 'en' ? 'en-GB' : 'vi-VN';
  const whenText = iso => { const d = new Date(iso); return isNaN(d) ? '—' : d.toLocaleString(localeTag()); };

  async function fetchJson(url) {
    const response = await fetch(url, {credentials: 'same-origin', headers: {Accept: 'application/json'}});
    if (response.status === 401) { location.href = '/login?returnTo=' + encodeURIComponent(location.pathname); throw new Error(t('Vui lòng đăng nhập để tiếp tục.')); }
    if (!response.ok) {
      let message = '';
      try { const body = await response.json(); message = body && body.message ? String(body.message) : ''; } catch (ignored) { /* not JSON */ }
      throw new Error(response.status === 403 ? t('Bạn không có quyền xem lịch sử tồn kho.') : (message || t('Không tải được lịch sử tồn kho.')));
    }
    return response.json();
  }

  /** Opens the history of one bike: where each colour stands, then the lines of the ledger, newest first. product = {id, name}. */
  function history(product) {
    return new Promise(resolve => {
      const dialog = document.createElement('dialog');
      dialog.className = 'stock-dialog stock-history-dialog';
      dialog.innerHTML = `
        <h2>${t('Lịch sử tồn kho')}</h2>
        <p class="muted">${esc(product.name || '')} · ${t('tồn hiện tại')} <strong data-stock>—</strong> ${t('xe')}</p>
        <div class="dialog-error" data-error hidden></div>
        <div class="table-scroll"><table class="stock-table stock-balances" data-balances hidden><thead><tr><th>${t('Màu')}</th><th>${t('Tồn kho')}</th><th>${t('Tổng các dòng sổ')}</th><th></th></tr></thead><tbody></tbody></table></div>
        <p class="muted" data-mismatch hidden>${t('Một số màu có tồn kho khác tổng các dòng sổ: đã có thay đổi ngoài sổ (ví dụ sửa trực tiếp trong cơ sở dữ liệu hoặc nhập danh mục).')}</p>
        <div class="filter-bar stock-history-filters">
          <label>${t('Màu')}<select data-colour><option value="">${t('Tất cả màu')}</option></select></label>
          <label>${t('Lý do')}<select data-reason><option value="">${t('Mọi lý do')}</option>${Object.entries(reasonText).map(([key, text]) => `<option value="${key}">${esc(text)}</option>`).join('')}</select></label>
        </div>
        <p class="muted" data-loading>${t('Đang tải…')}</p>
        <p class="muted" data-empty hidden>${t('Chưa có biến động tồn kho nào.')}</p>
        <div class="table-scroll"><table class="stock-table stock-lines" data-lines hidden><thead><tr><th>${t('Thời gian')}</th><th>${t('Màu')}</th><th>${t('Lý do')}</th><th>${t('Thay đổi')}</th><th>${t('Tồn sau')}</th><th>${t('Người làm')}</th><th>${t('Ghi chú')}</th></tr></thead><tbody></tbody></table></div>
        <p class="dialog-actions"><button type="button" class="button compact ghost" data-more hidden>${t('Xem thêm')}</button><button type="button" class="button compact" data-close>${t('Đóng')}</button></p>`;
      document.body.appendChild(dialog);

      const $ = selector => dialog.querySelector(selector);
      const base = `/api/v1/manager/products/${encodeURIComponent(product.id)}/stock-history`;
      let beforeId = null;
      let seq = 0;
      let coloursFilled = false;

      const reasonCell = line => {
        let text = esc(reasonText[line.reason] || line.reason);
        if (line.orderNumber) {
          text += line.orderId ? ` <a href="/manager/orders/${encodeURIComponent(line.orderId)}">${esc(line.orderNumber)}</a>` : ` ${esc(line.orderNumber)}`;
        }
        if (line.receiptNumber) {
          // the receipt page needs a permission the history page does not: link it only when the menu offers it, else show the number alone
          text += !document.querySelector('a[href="/manager/stock-receipts"]') ? ` ${esc(line.receiptNumber)}` : ` <a href="/manager/stock-receipts?q=${encodeURIComponent(line.receiptNumber)}">${esc(line.receiptNumber)}</a>`;
        }
        return text;
      };
      const rowHtml = line => `<tr>
        <td>${esc(whenText(line.createdAt))}</td>
        <td>${line.colorName ? esc(line.colorName) : '—'}</td>
        <td>${reasonCell(line)}</td>
        <td class="stock-change ${line.change > 0 ? 'pos' : 'neg'}">${line.change > 0 ? '+' : ''}${line.change}</td>
        <td>${line.balanceAfter}</td>
        <td>${line.actorName ? esc(line.actorName) : '—'}</td>
        <td>${line.note ? `<span class="clamped-text" title="${esc(line.note)}">${esc(line.note)}</span>` : '—'}</td></tr>`;

      async function load(reset) {
        const mine = ++seq;
        if (reset) { beforeId = null; $('[data-lines] tbody').innerHTML = ''; }
        $('[data-loading]').hidden = false;
        $('[data-error]').hidden = true;
        $('[data-more]').hidden = true;
        const query = new URLSearchParams();
        if ($('[data-colour]').value) query.set('variantId', $('[data-colour]').value);
        if ($('[data-reason]').value) query.set('reason', $('[data-reason]').value);
        if (beforeId != null) query.set('beforeId', beforeId);
        try {
          const data = await fetchJson(`${base}?${query}`);
          if (mine !== seq) return;
          $('[data-stock]').textContent = data.stockQuantity;
          if (data.balances && data.balances.length) {
            $('[data-balances] tbody').innerHTML = data.balances.map(b => `<tr><td>${b.colorName ? esc(b.colorName) : esc(data.productName)}${b.active ? '' : ` <small class="muted">(${t('ngừng bán')})</small>`}</td><td>${b.stock}</td><td>${b.ledgerTotal}</td><td>${b.matches ? `<span class="stock-tag ok">${t('Khớp')}</span>` : `<span class="stock-tag low">${t('Lệch')} ${b.stock - b.ledgerTotal > 0 ? '+' : ''}${b.stock - b.ledgerTotal}</span>`}</td></tr>`).join('');
            $('[data-balances]').hidden = false;
            $('[data-mismatch]').hidden = data.balances.every(b => b.matches);
            if (!coloursFilled) {
              const withColour = data.balances.filter(b => b.variantId != null);
              $('[data-colour]').innerHTML = `<option value="">${t('Tất cả màu')}</option>` + withColour.map(b => `<option value="${b.variantId}">${esc(b.colorName)}</option>`).join('');
              $('[data-colour]').closest('label').hidden = withColour.length === 0;
              coloursFilled = true;
            }
          }
          const items = data.items || [];
          $('[data-lines] tbody').insertAdjacentHTML('beforeend', items.map(rowHtml).join(''));
          const shown = $('[data-lines] tbody').children.length;
          $('[data-lines]').hidden = shown === 0;
          $('[data-empty]').hidden = shown !== 0;
          $('[data-empty]').textContent = $('[data-colour]').value || $('[data-reason]').value ? t('Không có dòng nào khớp bộ lọc.') : t('Chưa có biến động tồn kho nào.');
          beforeId = items.length ? items[items.length - 1].id : beforeId;
          $('[data-more]').hidden = !data.hasMore;
        } catch (error) {
          if (mine !== seq) return;
          $('[data-error]').textContent = error.message;
          $('[data-error]').hidden = false;
          $('[data-empty]').hidden = true;
        } finally {
          if (mine === seq) $('[data-loading]').hidden = true;
        }
      }

      dialog.addEventListener('change', event => { if (event.target.matches('[data-colour], [data-reason]')) load(true); });
      dialog.addEventListener('click', event => {
        if (event.target.closest('[data-more]')) load(false);
        if (event.target.closest('[data-close]')) dialog.close();
      });
      dialog.addEventListener('close', () => { dialog.remove(); resolve(); });
      dialog.showModal();
      load(true);
    });
  }

  return {open, history};
})();
