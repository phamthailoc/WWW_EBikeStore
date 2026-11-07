// Reviews on the product page (V8): summary, list, and a form for customers who received the bike.
(() => {
  const host = document.querySelector('#reviews');
  if (!host) return;
  const slug = host.dataset.slug;
  const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));
  const stars = n => '★'.repeat(n) + '☆'.repeat(5 - n);
  const date = iso => iso ? new Date(iso).toLocaleDateString('vi-VN') : '';

  async function api(url, method = 'GET', data) {
    const response = await fetch(url, {
      method,
      credentials: 'same-origin',
      headers: {Accept: 'application/json', 'Content-Type': 'application/json'},
      body: data === undefined ? undefined : JSON.stringify(data)
    });
    if (!response.ok) {
      let message = '';
      try { message = (await response.json()).message || ''; } catch (ignored) { /* not JSON */ }
      const error = Error(message || 'Không thể gửi đánh giá. Vui lòng thử lại.');
      error.status = response.status;
      throw error;
    }
    if (response.status === 204) return null;
    try { return await response.json(); } catch (ignored) { return null; }
  }

  host.innerHTML = `
    <h2>Đánh giá của khách hàng</h2>
    <div class="review-summary" data-summary></div>
    <div class="review-form-area" data-form-area></div>
    <ul class="review-list" data-list></ul>`;
  const summary = host.querySelector('[data-summary]');
  const formArea = host.querySelector('[data-form-area]');
  const list = host.querySelector('[data-list]');
  let reviews = [];
  let editing = null;

  function renderSummary() {
    if (!reviews.length) {
      summary.innerHTML = '<p class="muted">Chưa có đánh giá nào cho mẫu xe này.</p>';
      return;
    }
    const average = reviews.reduce((sum, r) => sum + r.rating, 0) / reviews.length;
    summary.innerHTML = `
      <div class="review-score"><strong>${average.toFixed(1).replace('.', ',')}</strong><span class="stars" aria-label="${average.toFixed(1)} trên 5 sao">${stars(Math.round(average))}</span><small>${reviews.length} đánh giá</small></div>
      <div class="review-bars">${[5, 4, 3, 2, 1].map(n => {
        const count = reviews.filter(r => r.rating === n).length;
        return `<div><span>${n} ★</span><i><b style="width:${Math.round(count * 100 / reviews.length)}%"></b></i><span>${count}</span></div>`;
      }).join('')}</div>`;
  }

  function renderList() {
    list.innerHTML = reviews.map(r => `
      <li data-id="${r.id}">
        <div class="review-head"><span class="stars">${stars(r.rating)}</span>
          <strong>${esc(r.authorName || r.username)}</strong>
          ${r.verifiedPurchase ? '<span class="badge">Đã mua hàng</span>' : ''}
          <small>${esc(date(r.createdAt))}</small></div>
        ${r.title ? `<h3>${esc(r.title)}</h3>` : ''}
        ${r.comment ? `<p>${esc(r.comment)}</p>` : ''}
        ${r.editableByCurrentUser ? '<p class="review-actions"><button type="button" data-do="edit">Sửa</button> <button type="button" data-do="delete">Xóa</button></p>' : ''}
      </li>`).join('');
  }

  function renderForm(eligibility) {
    if (!editing && !eligibility.canReview) {
      const needsLogin = /Đăng nhập/.test(eligibility.reason || '');
      formArea.innerHTML = `<p class="muted">${esc(eligibility.reason || '')}${needsLogin ? ` <a href="/login?returnTo=${encodeURIComponent(location.pathname)}">Đăng nhập</a>` : ''}</p>`;
      return;
    }
    const r = editing || {rating: 5, title: '', comment: ''};
    formArea.innerHTML = `
      <form class="review-form">
        <h3>${editing ? 'Sửa đánh giá của bạn' : 'Viết đánh giá'}</h3>
        <fieldset class="star-input"><legend>Số sao</legend>
          ${[5, 4, 3, 2, 1].map(n => `<input type="radio" id="star${n}" name="rating" value="${n}" ${r.rating === n ? 'checked' : ''}><label for="star${n}" title="${n} sao">★</label>`).join('')}
        </fieldset>
        <label>Tiêu đề (không bắt buộc)<input name="title" maxlength="255" value="${esc(r.title || '')}"></label>
        <label>Nhận xét<textarea name="comment" rows="4" maxlength="2000">${esc(r.comment || '')}</textarea></label>
        <p class="review-error" role="alert" hidden></p>
        <p><button class="button compact" type="submit">${editing ? 'Lưu đánh giá' : 'Gửi đánh giá'}</button>
        ${editing ? '<button class="button compact ghost" type="button" data-do="cancel">Hủy</button>' : ''}</p>
      </form>`;
    const form = formArea.querySelector('form');
    form.addEventListener('submit', async event => {
      event.preventDefault();
      const error = form.querySelector('.review-error');
      error.hidden = true;
      const body = {rating: Number(form.rating.value), title: form.title.value.trim() || null, comment: form.comment.value.trim() || null};
      try {
        if (editing) await api(`/api/v1/reviews/${editing.id}`, 'PUT', body);
        else await api(`/api/v1/products/${encodeURIComponent(slug)}/reviews`, 'POST', body);
        editing = null;
        await load();
      } catch (failure) {
        error.textContent = failure.message;
        error.hidden = false;
      }
    });
  }

  async function load() {
    const [items, eligibility] = await Promise.all([
      api(`/api/v1/products/${encodeURIComponent(slug)}/reviews`),
      api(`/api/v1/products/${encodeURIComponent(slug)}/reviews/eligibility`).catch(() => ({canReview: false, reason: ''}))
    ]);
    reviews = items;
    renderSummary();
    renderList();
    renderForm(eligibility);
  }

  host.addEventListener('click', async event => {
    const button = event.target.closest('[data-do]');
    if (!button) return;
    const item = button.closest('li');
    try {
      if (button.dataset.do === 'edit') {
        editing = reviews.find(r => r.id === Number(item.dataset.id));
        renderForm({canReview: false});
        formArea.scrollIntoView({block: 'center'});
      } else if (button.dataset.do === 'cancel') {
        editing = null;
        await load();
      } else if (button.dataset.do === 'delete') {
        if (!confirm('Xóa đánh giá này?')) return;
        await api(`/api/v1/reviews/${item.dataset.id}`, 'DELETE');
        await load();
      }
    } catch (failure) {
      alert(failure.message);
    }
  });

  load().catch(() => {
    summary.innerHTML = '<p class="muted">Không tải được đánh giá. Vui lòng tải lại trang.</p>';
  });
})();
