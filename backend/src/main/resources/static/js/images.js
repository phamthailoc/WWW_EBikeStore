// Product image manager for the manager and admin product tables (V7): upload several images,
// choose the main image, reorder and delete. Listens in the capture phase so it replaces the old
// single-file upload in workspace.js.
(() => {
  const notice = document.querySelector('#notice');
  const tell = (message, error = false) => {
    notice.textContent = message;
    notice.hidden = false;
    notice.classList.toggle('error', error);
    notice.scrollIntoView({block: 'nearest'});
  };
  const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));
  const BASE = '/api/v1/admin/product-images';

  async function call(url, options = {}) {
    const response = await fetch(url, {credentials: 'same-origin', headers: {Accept: 'application/json'}, ...options});
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

  const form = fields => {
    const data = new FormData();
    Object.entries(fields).forEach(([key, value]) => { if (value !== undefined && value !== null) data.append(key, value); });
    return data;
  };

  async function open(productId, productName, slug) {
    // Colours let an image belong to one colour; a product without colours only has shared images.
    let colours = [];
    if (slug) {
      try {
        const detail = await call(`/api/v1/products/${encodeURIComponent(slug)}`);
        colours = (detail.variants || []).filter(v => v.active !== false);
      } catch (ignored) { /* shared images still work */ }
    }
    const colourName = id => id == null ? 'Chung cho cả sản phẩm' : (colours.find(c => c.id === id)?.colorName || `Màu #${id}`);

    const dialog = document.createElement('dialog');
    dialog.className = 'image-dialog';
    dialog.innerHTML = `
      <div class="image-manager">
        <h2>Ảnh sản phẩm</h2>
        <p class="muted">${esc(productName || 'Sản phẩm #' + productId)}. Ảnh đầu tiên của mỗi nhóm là ảnh đại diện nếu chưa chọn khác.</p>
        <p class="dialog-error" role="alert" hidden></p>
        <div class="image-grid" data-grid></div>
        <form class="image-upload" data-upload>
          <label>Gán cho
            <select name="variantId">
              <option value="">Chung cho cả sản phẩm</option>
              ${colours.map(c => `<option value="${c.id}">${esc(c.colorName || c.variantName)}</option>`).join('')}
            </select>
          </label>
          <label>Chọn ảnh (có thể chọn nhiều)<input type="file" name="files" accept="image/*" multiple required></label>
          <p><button class="button compact" type="submit">Tải ảnh lên</button> <span data-progress class="muted"></span></p>
        </form>
        <p class="dialog-actions"><button type="button" class="button compact ghost" data-close>Đóng</button></p>
      </div>`;
    document.body.appendChild(dialog);
    const grid = dialog.querySelector('[data-grid]');
    const error = dialog.querySelector('.dialog-error');
    const progress = dialog.querySelector('[data-progress]');
    let images = [];

    const fail = failure => { error.textContent = failure.message; error.hidden = false; };
    const sorted = list => [...list].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || a.id - b.id);

    function render() {
      grid.innerHTML = images.length ? images.map((image, index) => `
        <figure data-id="${image.id}">
          <img src="${esc(image.imageUrl)}" alt="${esc(image.altText || '')}" loading="lazy">
          <figcaption>
            ${image.primaryImage ? '<span class="badge">Ảnh đại diện</span> ' : ''}<small>${esc(colourName(image.variantId))}</small>
            <span class="image-actions">
              <button type="button" class="table-action" data-do="left" ${index === 0 ? 'disabled' : ''} aria-label="Chuyển lên trước">←</button>
              <button type="button" class="table-action" data-do="right" ${index === images.length - 1 ? 'disabled' : ''} aria-label="Chuyển ra sau">→</button>
              ${image.primaryImage ? '' : '<button type="button" class="table-action" data-do="primary">Đặt làm ảnh đại diện</button>'}
              <button type="button" class="table-action" data-do="delete">Xóa</button>
            </span>
          </figcaption>
        </figure>`).join('') : '<p class="muted">Sản phẩm chưa có ảnh nào.</p>';
    }

    async function refresh() {
      images = sorted(await call(`${BASE}/by-product/${productId}`));
      render();
    }

    grid.addEventListener('click', async event => {
      const button = event.target.closest('[data-do]');
      if (!button) return;
      error.hidden = true;
      const index = images.findIndex(i => i.id === Number(button.closest('figure').dataset.id));
      const image = images[index];
      try {
        if (button.dataset.do === 'delete') {
          if (!confirm('Xóa ảnh này?')) return;
          await call(`${BASE}/${image.id}`, {method: 'DELETE'});
        } else if (button.dataset.do === 'primary') {
          await call(`${BASE}/${image.id}`, {method: 'PUT', body: form({primaryImage: true})});
        } else {
          const target = button.dataset.do === 'left' ? index - 1 : index + 1;
          const order = [...images];
          [order[index], order[target]] = [order[target], order[index]];
          // Renumber the whole list so equal or missing sort orders cannot make the move a no-op.
          for (let position = 0; position < order.length; position++) {
            if (order[position].sortOrder !== position) {
              await call(`${BASE}/${order[position].id}`, {method: 'PUT', body: form({sortOrder: position})});
            }
          }
        }
        await refresh();
      } catch (failure) {
        fail(failure);
      }
    });

    dialog.querySelector('[data-upload]').addEventListener('submit', async event => {
      event.preventDefault();
      error.hidden = true;
      const upload = event.currentTarget;
      const files = [...upload.files.files];
      const variantId = upload.variantId.value ? Number(upload.variantId.value) : null;
      const submit = upload.querySelector('[type=submit]');
      submit.disabled = true;
      let done = 0;
      try {
        for (const file of files) {
          progress.textContent = `Đang tải ${done + 1}/${files.length}…`;
          const sameGroup = images.filter(i => (i.variantId ?? null) === variantId);
          await call(BASE, {method: 'POST', body: form({
            productId,
            variantId,
            file,
            altText: file.name.replace(/\.[^.]+$/, ''),
            sortOrder: images.length ? Math.max(...images.map(i => i.sortOrder ?? 0)) + 1 : 0,
            primaryImage: sameGroup.length === 0
          })});
          done++;
          await refresh();
        }
        upload.reset();
        progress.textContent = `Đã tải ${done} ảnh.`;
      } catch (failure) {
        fail(failure);
        progress.textContent = done ? `Đã tải ${done}/${files.length} ảnh.` : '';
      } finally {
        submit.disabled = false;
      }
    });

    dialog.querySelector('[data-close]').addEventListener('click', () => dialog.close());
    dialog.addEventListener('close', () => {
      dialog.remove();
      window.dispatchEvent(new Event('workspace:reload'));
    });
    dialog.showModal();
    try { await refresh(); } catch (failure) { fail(failure); }
  }

  document.addEventListener('click', event => {
    const button = event.target.closest('[data-action="images"]');
    if (!button) return;
    event.stopImmediatePropagation();
    open(Number(button.dataset.id), button.dataset.name, button.dataset.slug).catch(failure => tell(failure.message, true));
  }, true);
})();
