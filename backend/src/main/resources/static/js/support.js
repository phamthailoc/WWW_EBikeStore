// Support page (Task 14): frequently asked questions, a request form, and the showroom list.
window.KineticSupport = (() => {
  const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));

  async function sendRequest(body) {
    const response = await fetch('/api/v1/support-requests', {
      method: 'POST',
      credentials: 'same-origin',
      headers: {'Content-Type': 'application/json', Accept: 'application/json'},
      body: JSON.stringify(body)
    });
    if (!response.ok) {
      let message = '';
      try { message = (await response.json()).message || ''; } catch (ignored) { /* not JSON */ }
      throw Error(message || t('Không gửi được yêu cầu. Vui lòng thử lại sau.'));
    }
  }

  function faqHtml(faq) {
    if (!faq.length) return '<p class="muted">' + t('Chưa có câu hỏi thường gặp.') + '</p>';
    const groups = new Map();
    faq.forEach(item => {
      if (!groups.has(item.category)) groups.set(item.category, []);
      groups.get(item.category).push(item);
    });
    return [...groups].map(([category, items]) => `
      <div class="faq-group"><h3>${esc(category)}</h3>
        ${items.map(item => `<details><summary>${esc(item.question)}</summary><p>${esc(item.answer)}</p></details>`).join('')}
      </div>`).join('');
  }

  function render(target, showrooms, faq) {
    target.innerHTML = `
      <section class="support-section"><p class="eyebrow">${t('KINETIC / CÂU HỎI THƯỜNG GẶP')}</p><h2>${t('Câu hỏi thường gặp')}</h2>${faqHtml(faq)}</section>
      <section class="support-section"><p class="eyebrow">${t('KINETIC / LIÊN HỆ')}</p><h2>${t('Gửi yêu cầu hỗ trợ')}</h2>
        <p class="muted">${t('Không tìm thấy câu trả lời? Gửi câu hỏi hoặc phản ánh, cửa hàng sẽ trả lời qua email.')}</p>
        <form class="support-form" id="supportForm" novalidate>
          <label>${t('Họ và tên')} <b>*</b><input name="customerName" required maxlength="100" autocomplete="name"></label>
          <label>Email <b>*</b><input name="email" type="email" required maxlength="255" autocomplete="email"></label>
          <label>${t('Số điện thoại (không bắt buộc)')}<input name="phone" inputmode="tel" maxlength="30" autocomplete="tel" placeholder="0901234567"></label>
          <label>${t('Tiêu đề')} <b>*</b><input name="subject" required maxlength="150"></label>
          <label class="wide">${t('Nội dung')} <b>*</b><textarea name="message" required rows="5" minlength="10" maxlength="3000" placeholder="${t('Mô tả vấn đề của bạn (ít nhất 10 ký tự)')}"></textarea></label>
          <p class="support-message" role="status" hidden></p>
          <p class="wide"><button class="button compact" type="submit">${t('Gửi yêu cầu')}</button></p>
        </form>
      </section>
      <section class="support-section"><p class="eyebrow">KINETIC / SHOWROOM</p><h2>${t('Cửa hàng của chúng tôi')}</h2>
        ${showrooms.length ? `<div class="flow-cards">${showrooms.map(s => `<article class="flow-card"><p class="eyebrow">KINETIC / SHOWROOM</p><h3>${esc(s.name)}</h3><p>${esc(s.address || '')}${s.district ? ` · ${esc(s.district)}` : ''}</p><p>${esc(s.phone || '')}</p></article>`).join('')}</div>` : '<p class="empty">' + t('Chưa có cửa hàng nhận xe.') + '</p>'}
      </section>`;

    const form = target.querySelector('#supportForm');
    const message = form.querySelector('.support-message');
    const show = (text, error) => {
      message.textContent = text;
      message.classList.toggle('error', Boolean(error));
      message.hidden = false;
    };

    // Signed-in customers get their name and email filled in; guests just see an empty form.
    fetch('/api/v1/auth/profile', {credentials: 'same-origin', headers: {Accept: 'application/json'}})
      .then(r => (r.ok ? r.json() : null))
      .then(profile => {
        if (!profile) return;
        if (!form.customerName.value) form.customerName.value = [profile.firstName, profile.lastName].filter(Boolean).join(' ');
        if (!form.email.value) form.email.value = profile.email || '';
      })
      .catch(() => { /* optional */ });

    form.addEventListener('submit', async event => {
      event.preventDefault();
      message.hidden = true;
      if (!form.checkValidity()) {
        form.reportValidity();
        return;
      }
      const button = form.querySelector('[type=submit]');
      button.disabled = true;
      try {
        await sendRequest({
          customerName: form.customerName.value.trim(),
          email: form.email.value.trim(),
          phone: form.phone.value.trim(),
          subject: form.subject.value.trim(),
          message: form.message.value.trim()
        });
        form.subject.value = '';
        form.message.value = '';
        show(t('Đã gửi yêu cầu. Cửa hàng sẽ phản hồi qua email của bạn.'), false);
      } catch (failure) {
        show(failure.message, true);
      } finally {
        button.disabled = false;
      }
    });
  }

  return {render};
})();
