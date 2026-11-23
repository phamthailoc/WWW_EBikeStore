// V27: downloads of the sales receipt and the warranty card (PDF). A button carries data-doc-url (and data-doc-name as the fallback file name);
// the file is fetched with the signed-in session so that a refusal shows in the page's notice instead of a raw JSON page, then saved from memory.
window.KineticDocuments = (() => {
  const tell = (message, error) => {
    const box = document.querySelector('#notice');
    if (!box) return;
    box.textContent = message;
    box.hidden = false;
    box.classList.toggle('error', Boolean(error));
  };

  async function errorMessage(response, fallback) {
    try {
      const body = await response.json();
      if (body && typeof body.message === 'string' && body.message.trim()) return body.message.trim();
    } catch (ignored) {
      // not JSON: the fallback below
    }
    return fallback;
  }

  async function download(url, fallbackName) {
    const response = await fetch(url, {credentials: 'same-origin', headers: {Accept: 'application/pdf, application/json'}});
    if (response.status === 401) {
      location.href = '/login?returnTo=' + encodeURIComponent(location.pathname);
      throw Error(t('Vui lòng đăng nhập để tiếp tục.'));
    }
    if (!response.ok) throw Error(await errorMessage(response, t('Không tải được tệp PDF. Vui lòng thử lại.')));
    const blob = await response.blob();
    const match = /filename="?([^";]+)"?/i.exec(response.headers.get('Content-Disposition') || '');
    const href = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = href;
    link.download = match ? match[1] : (fallbackName || 'document.pdf');
    link.rel = 'noopener';
    document.body.appendChild(link);
    link.click();
    link.remove();
    // a phone may still be opening the file when the click returns: keep the address alive for a minute
    setTimeout(() => URL.revokeObjectURL(href), 60000);
  }

  let busy = false;
  document.addEventListener('click', async event => {
    const button = event.target.closest('[data-doc-url]');
    if (!button) return;
    event.preventDefault();
    if (busy) return;
    busy = true;
    const label = button.textContent;
    button.disabled = true;
    button.textContent = t('Đang tạo PDF…');
    try {
      await download(button.dataset.docUrl, button.dataset.docName);
      tell(t('Đã tải tệp PDF.'));
    } catch (error) {
      tell(error.message, true);
    } finally {
      busy = false;
      button.disabled = false;
      button.textContent = label;
    }
  });

  return {download};
})();
