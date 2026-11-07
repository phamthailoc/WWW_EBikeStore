// Customer area (V11): unread-notification badge in the header, live through the server's event stream,
// and a "mark all as read" button on the notifications page.
(() => {
  const actions = document.querySelector('header.top .actions');
  if (!actions) return;

  const link = document.createElement('a');
  link.className = 'notif-link';
  link.href = '/customer/notifications';
  link.innerHTML = 'Thông báo <span class="notif-badge" id="notifBadge" hidden></span>';
  actions.insertBefore(link, actions.firstChild);
  const badge = link.querySelector('#notifBadge');

  const show = count => {
    const n = Number(count) || 0;
    badge.textContent = n > 99 ? '99+' : String(n);
    badge.hidden = n <= 0;
    link.setAttribute('aria-label', n > 0 ? `Thông báo, ${n} chưa đọc` : 'Thông báo');
  };

  async function refresh() {
    try {
      const response = await fetch('/api/v1/notifications/unread-count', {credentials: 'same-origin', headers: {Accept: 'application/json'}});
      if (response.ok) show((await response.json()).unreadCount);
    } catch (ignored) { /* the badge simply stays as it was */ }
  }

  refresh();
  if (window.EventSource) {
    const stream = new EventSource('/api/v1/notifications/stream');
    stream.addEventListener('unread-count', event => {
      try { show(JSON.parse(event.data).unreadCount); } catch (ignored) { /* ignore a malformed event */ }
    });
    window.addEventListener('pagehide', () => stream.close());
  }

  // Reading one notification happens in workspace.js; look at the count again shortly afterwards.
  document.addEventListener('click', event => {
    if (event.target.closest('[data-action="read"]')) setTimeout(refresh, 700);
  });

  if (document.body.dataset.page === '/customer/notifications') {
    const heading = document.querySelector('#data-panel .product-action') || document.querySelector('#data-panel .panel-heading');
    if (!heading) return;
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'button compact';
    button.textContent = 'Đánh dấu tất cả đã đọc';
    heading.appendChild(button);
    button.addEventListener('click', async () => {
      button.disabled = true;
      const notice = document.querySelector('#notice');
      try {
        const response = await fetch('/api/v1/notifications/read-all', {method: 'PATCH', credentials: 'same-origin', headers: {Accept: 'application/json'}});
        if (!response.ok) throw Error('Không thể đánh dấu đã đọc. Vui lòng thử lại.');
        show(0);
        window.dispatchEvent(new Event('workspace:reload'));
        notice.textContent = 'Đã đánh dấu tất cả thông báo là đã đọc.';
        notice.classList.remove('error');
        notice.hidden = false;
      } catch (failure) {
        notice.textContent = failure.message;
        notice.classList.add('error');
        notice.hidden = false;
      } finally {
        button.disabled = false;
      }
    });
  }
})();
