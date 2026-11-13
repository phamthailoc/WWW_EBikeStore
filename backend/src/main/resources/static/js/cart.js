(() => {
  'use strict';
  const KEY = 'kinetic_cart';
  const key = item => `${item.id}:${item.variantId || ''}`;
  const money = value => `${new Intl.NumberFormat('vi-VN').format(Number(value) || 0)} ₫`;
  const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const safeImage = value => typeof value === 'string' && /^(\/[^/]|https?:\/\/)/.test(value) ? value : '/images/hero-fallback.svg';
  const normalize = items => Array.isArray(items) ? items.filter(i => i && Number.isInteger(Number(i.id)) && Number(i.id) > 0).slice(0, 20).map(i => ({...i, id:Number(i.id), variantId:i.variantId ? Number(i.variantId) : null, quantity:Math.max(1, Math.min(10, Math.trunc(Number(i.quantity) || 1)))})) : [];
  function read() {
    try { return normalize(JSON.parse(localStorage.getItem(KEY) || '[]')); } catch { return []; }
  }
  function write(items) {
    localStorage.setItem(KEY, JSON.stringify(normalize(items)));
    window.dispatchEvent(new Event('kinetic:cart'));
  }
  function limit(item) { return Math.max(0, Math.min(10, item.stock == null ? 10 : Number(item.stock))); }
  function add(item) {
    const items = read(), existing = items.find(i => key(i) === key(item));
    const quantity = (existing?.quantity || 0) + item.quantity;
    if (quantity > limit(item)) throw Error(`${t('Chỉ có thể mua tối đa')} ${limit(item)} ${t('xe cho màu đã chọn.')}`);
    if (!existing && items.length >= 20) throw Error(t('Giỏ hàng tối đa 20 mẫu/màu xe.'));
    if (existing) Object.assign(existing, item, {quantity}); else items.push({...item});
    write(items);
  }
  async function request(url, data) {
    const response = await fetch(url, {credentials:'same-origin', method:data === undefined ? 'GET' : 'POST', headers:{Accept:'application/json', ...(data === undefined ? {} : {'Content-Type':'application/json'})}, ...(data === undefined ? {} : {body:JSON.stringify(data)})});
    const body = await response.json().catch(() => null);
    if (!response.ok) throw Error(body?.detail || body?.message || body?.error || `${t('Yêu cầu thất bại (')}${response.status}).`);
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
      link.href = '/cart'; link.className = 'cart-link'; link.setAttribute('aria-label', t('Mở giỏ hàng'));
      link.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><path d="M3 3h2l2 12h12l2-9H6"/><circle cx="9" cy="20" r="1"/><circle cx="18" cy="20" r="1"/></svg><span>' + t('Giỏ hàng') + '</span><b data-cart-count>0</b>';
      const actions = header.querySelector('.actions');
      if (actions) actions.prepend(link); else header.append(link);
    });
    updateHeader();
  }
  window.KineticCart = {read, write, add, key, limit, money, escape, safeImage, request, refresh};
  window.addEventListener('kinetic:cart', updateHeader);
  window.addEventListener('storage', event => {if (event.key === KEY) updateHeader();});
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', mountHeader); else mountHeader();
})();
