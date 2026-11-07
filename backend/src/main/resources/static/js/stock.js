// Per-colour stock editor for the manager and admin product tables (V17).
// A product with colours sells from its colours, so its total is the sum of what each colour has left.
window.KineticStock = (() => {
  const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));

  /**
   * Opens the editor for a product detail (from /api/v1/products/{slug}).
   * save(url, method, body) sends the request and returns the parsed response; say(message, isError) shows a notice.
   * Resolves when the dialog is closed.
   */
  function open(product, save, say) {
    return new Promise(resolve => {
      const dialog = document.createElement('dialog');
      dialog.className = 'stock-dialog';
      const colours = (product.variants || []).filter(v => v.active !== false);
      let total = colours.reduce((sum, v) => sum + (v.stockQuantity || 0), 0);
      dialog.innerHTML = `
        <form method="dialog">
          <h2>Tồn kho theo màu</h2>
          <p class="muted">${esc(product.name)} · tổng <strong data-total>${total}</strong> xe</p>
          <table class="stock-table"><tbody>
            ${colours.map(v => `<tr data-variant="${v.id}">
              <td><span class="swatch" style="background:${esc(v.colorHex || '#ccc')}"></span> ${esc(v.colorName || v.variantName)}</td>
              <td><input type="number" min="0" step="1" value="${v.stockQuantity || 0}" aria-label="Tồn kho ${esc(v.colorName || v.variantName)}"></td>
              <td><button type="button" class="table-action" data-save>Lưu</button></td></tr>`).join('')}
          </tbody></table>
          <p><button class="button compact" value="close">Đóng</button></p>
        </form>`;
      document.body.appendChild(dialog);

      dialog.addEventListener('click', async event => {
        const button = event.target.closest('[data-save]');
        if (!button) return;
        const row = button.closest('tr');
        const input = row.querySelector('input');
        const quantity = Number(input.value);
        if (!Number.isInteger(quantity) || quantity < 0) {
          say('Số lượng tồn kho phải là số nguyên không âm.', true);
          return;
        }
        button.disabled = true;
        try {
          const summary = await save(`/api/v1/manager/products/${product.id}/variants/${row.dataset.variant}/stock`, 'PATCH', {stockQuantity: quantity});
          total = summary && summary.stockQuantity != null ? summary.stockQuantity : total;
          dialog.querySelector('[data-total]').textContent = total;
          say('Đã cập nhật tồn kho.');
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

  return {open};
})();
