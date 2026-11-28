(() => {
  'use strict';
  const LEGACY_KEY = 'kinetic_cart'; // the cart used to live in localStorage; a leftover one is moved to the session once
  const ENDPOINT = '/api/v1/cart';
  let current = [];
  const key = item => `${item.id}:${item.variantId || ''}`;
  const money = value => `${new Intl.NumberFormat('vi-VN').format(Number(value) || 0)} ₫`;
  const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const safeImage = value => typeof value === 'string' && /^(\/[^/]|https?:\/\/)/.test(value) ? value : '/images/hero-fallback.svg';
  const normalize = items => Array.isArray(items) ? items.filter(i => i && Number.isInteger(Number(i.id)) && Number(i.id) > 0).slice(0, 20).map(i => ({...i, id:Number(i.id), variantId:i.variantId ? Number(i.variantId) : null, quantity:Math.max(1, Math.min(10, Math.trunc(Number(i.quantity) || 1)))})) : [];
  // The cart is kept in the server-side session; this page holds a copy that read() returns at once. Wait for ready before the first read().
  function read() { return current.map(item => ({...item})); }
  let queue = Promise.resolve();
  let writeGen = 0;   // counts local changes: an answer from the server is adopted only when nothing newer has happened since it was asked
  let pending = 0;    // requests on their way to the server
  let loaded = false; // true once the cart has been read from the server at least once
  const lineOf = i => ({id: i.id, variantId: i.variantId || null, quantity: i.quantity, slug: i.slug});
  function adopt(lines, shown) {
    const extra = new Map((shown || []).map(i => [key(i), i]));
    current = normalize(lines).map(line => ({...(extra.get(key(line)) || {}), ...line}));
    window.dispatchEvent(new Event('kinetic:cart'));
  }
  // Reads the cart from the server (after the writes before it) and shows it, unless the visitor changed it meanwhile.
  function reload() {
    const gen = writeGen;
    pending++;
    const job = queue.then(async () => {
      const lines = await request(ENDPOINT);
      loaded = true;
      if (gen === writeGen) adopt(lines, current);
    }).finally(() => { pending--; });
    queue = job.catch(() => {});
    return job;
  }
  // Changes are sent one after another so an older answer never overwrites a newer cart. The returned promise rejects when the server refuses;
  // the page then goes back to what the server really has.
  function write(items) {
    const shown = normalize(items);
    const gen = ++writeGen;
    current = shown;
    window.dispatchEvent(new Event('kinetic:cart'));
    pending++;
    const send = queue.then(async () => {
      if (!loaded) await reload();
      const lines = await request(ENDPOINT, shown.map(lineOf), 'PUT');
      loaded = true;
      if (gen === writeGen) adopt(lines, shown);
    }).catch(async error => {
      if (gen === writeGen) { try { adopt(await request(ENDPOINT), shown); } catch { /* keep what is shown */ } }
      throw error;
    }).finally(() => { pending--; });
    queue = send.catch(() => {});
    send.catch(() => {});
    return send;
  }
  const ready = (async () => {
    try {
      let lines = await request(ENDPOINT);
      loaded = true;
      let legacy = [];
      try { legacy = normalize(JSON.parse(localStorage.getItem(LEGACY_KEY) || '[]')); } catch { legacy = []; }
      if (legacy.length) {
        if (!lines.length) { try { lines = await request(ENDPOINT, legacy.map(lineOf), 'PUT'); } catch { /* the old lines are dropped */ } }
        try { localStorage.removeItem(LEGACY_KEY); } catch { /* ignore */ }
      }
      adopt(lines, legacy);
    } catch { current = []; }
  })();
  function limit(item) { return Math.max(0, Math.min(10, item.stock == null ? 10 : Number(item.stock))); }
  async function add(item) {
    await ready;
    if (!loaded) await reload(); // never write on top of a cart that could not be read
    const items = read(), existing = items.find(i => key(i) === key(item));
    const quantity = (existing?.quantity || 0) + item.quantity;
    if (quantity > limit(item)) throw Error(`${'Chỉ có thể mua tối đa'} ${limit(item)} ${'xe cho màu đã chọn.'}`);
    // the server limit is 10 bikes per model, whatever the colours
    const sameModel = items.filter(i => Number(i.id) === Number(item.id) && key(i) !== key(item)).reduce((sum, i) => sum + i.quantity, 0);
    if (sameModel + quantity > 10) throw Error(`${'Chỉ có thể mua tối đa'} 10 ${'xe cho mỗi mẫu, tính cả các màu.'}`);
    if (!existing && items.length >= 20) throw Error('Giỏ hàng tối đa 20 mẫu/màu xe.');
    if (existing) Object.assign(existing, item, {quantity}); else items.push({...item});
    await write(items);
  }
  async function request(url, data, method, extraHeaders) {
    const response = await fetch(url, {credentials:'same-origin', method:method || (data === undefined ? 'GET' : 'POST'), headers:{Accept:'application/json', ...(data === undefined ? {} : {'Content-Type':'application/json'}), ...(extraHeaders || {})}, ...(data === undefined ? {} : {body:JSON.stringify(data)})});
    const body = await response.json().catch(() => null);
    if (response.status === 204) return null;
    if (!response.ok) { const error = Error(body?.detail || body?.message || body?.error || `${'Yêu cầu thất bại ('}${response.status}).`); error.status = response.status; throw error; }
    return body;
  }
  async function refresh(items) {
    const products = new Map();
    await Promise.all([...new Set(items.map(i => i.slug))].map(async slug => {
      try { products.set(slug, await request(`/api/v1/products/${encodeURIComponent(slug)}`)); }
      catch { products.set(slug, null); }
    }));
    return items.map(item => {
      const product = products.get(item.slug);
      const variant = product?.variants?.find(v => Number(v.id) === Number(item.variantId));
      if (!product || (item.variantId && !variant)) return {...item, stock:0, unavailable:true};
      const stock = Math.min(Number(product.stockQuantity || 0), variant ? Number(variant.stockQuantity || 0) : Infinity);
      return {...item, name:product.name, image:variant?.imageUrl || product.images?.[0], colorName:variant?.colorName || '', price:Number(product.discountPrice ?? product.price) + Number(variant?.additionalPrice || 0), stock, unavailable:false};
    });
  }
  function updateHeader() {
    document.querySelectorAll('[data-cart-count]').forEach(node => {node.textContent = read().reduce((sum, i) => sum + i.quantity, 0);});
  }
  function mountHeader() {
    document.querySelectorAll('header.top').forEach(header => {
      if (header.querySelector('.cart-link')) return;
      const link = document.createElement('a');
      link.href = '/cart'; link.className = 'cart-link'; link.setAttribute('aria-label', 'Mở giỏ hàng');
      link.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><path d="M3 3h2l2 12h12l2-9H6"/><circle cx="9" cy="20" r="1"/><circle cx="18" cy="20" r="1"/></svg><span>' + 'Giỏ hàng' + '</span><b data-cart-count>0</b>';
      const actions = header.querySelector('.actions');
      if (actions) actions.prepend(link); else header.append(link);
    });
    updateHeader();
  }
  window.KineticCart = {read, write, add, key, limit, money, escape, safeImage, request, refresh, ready, reload, idle: () => pending === 0};
  window.addEventListener('kinetic:cart', updateHeader);
  // another tab of the same session may have changed the cart
  window.addEventListener('focus', () => { reload().catch(() => {}); });
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', mountHeader); else mountHeader();
})();
