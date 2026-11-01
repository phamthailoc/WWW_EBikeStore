(() => {
  const flow=document.body.dataset.mode||'',api=document.body.dataset.api||'',target=document.querySelector('#flow-extra'),notice=document.querySelector('#notice');
  const escape=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const get=async url=>{const r=await fetch(url,{credentials:'same-origin',headers:{Accept:'application/json'}});if(r.status===401){location.href='/login?returnTo='+encodeURIComponent(location.pathname);throw Error('Vui lòng đăng nhập để tiếp tục.')}if(!r.ok)throw Error(await r.text());return r.status===204?null:r.json()};
  const post=async(url,data,method='POST')=>{const r=await fetch(url,{method,credentials:'same-origin',headers:{'Content-Type':'application/json',Accept:'application/json'},body:JSON.stringify(data)});if(r.status===401){location.href='/login';throw Error('Vui lòng đăng nhập để tiếp tục.')}if(!r.ok)throw Error(await r.text());return r.status===204?null:r.json()};
  const tell=(s,error=false)=>{notice.textContent=s;notice.hidden=false;notice.classList.toggle('error',error)};
  const cart=()=>{try{return JSON.parse(localStorage.getItem('kinetic_cart')||'[]')}catch{return[]}};
  const finish=()=>{document.querySelector('#loading').hidden=true};
  const productCard=p=>`<article class="favorite-card"><a href="/product/${encodeURIComponent(p.slug)}"><img src="${escape(p.images?.[0]||'/images/hero-fallback.svg')}" alt="${escape(p.name)}"><p class="eyebrow">${escape(p.category?.name||'KINETIC SELECT')}</p><h2>${escape(p.name)}</h2><p class="favorite-price">${Number(p.discountPrice||p.price||0).toLocaleString('vi-VN')} ₫</p></a><div class="product-action"><a class="button compact ghost" href="/product/${encodeURIComponent(p.slug)}">Xem chi tiết</a><button class="table-action" data-remove-product="${p.id}">${flow==='favorites'?'Bỏ yêu thích':'Bỏ so sánh'}</button></div></article>`;
  const renderProducts=(products,emptyText)=>{finish();if(!products.length){target.innerHTML=`<p class="empty">${emptyText}</p>`;return}target.innerHTML=`<div class="favorite-grid">${products.map(productCard).join('')}</div>`};
  target?.addEventListener('click',async e=>{const b=e.target.closest('[data-remove-product]');if(!b)return;const id=Number(b.dataset.removeProduct);try{if(flow==='favorites')await post(`/api/v1/favorites/${id}`,{},'DELETE');else{const ids=JSON.parse(localStorage.getItem('kinetic_compare')||'[]').filter(x=>Number(x)!==id);localStorage.setItem('kinetic_compare',JSON.stringify(ids));location.reload();return}location.reload()}catch(err){tell(err.message,true)}});
  const renderCompare=products=>{finish();if(!products.length){target.innerHTML='<p class="empty">Chưa tìm thấy mẫu xe trong danh sách so sánh.</p>';return}const rows=[['Giá bán',p=>`${Number(p.discountPrice||p.price||0).toLocaleString('vi-VN')} ₫`],['Tồn kho',p=>`${p.stockQuantity??0} xe`],['Loại xe',p=>p.specification?.vehicleType],['Thương hiệu',p=>p.specification?.brand],['Pin',p=>`${p.specification?.batteryType||'—'}${p.specification?.batteryCapacityAh?` · ${p.specification.batteryCapacityAh} Ah`:''}`],['Tốc độ tối đa',p=>p.specification?.maxSpeedKmh?`${p.specification.maxSpeedKmh} km/h`:null],['Quãng đường',p=>p.specification?.maxRangeKm?`${p.specification.maxRangeKm} km`:null],['Công suất',p=>p.specification?.motorPowerWatts?`${p.specification.motorPowerWatts} W`:null],['Phanh',p=>p.specification?.brakeType],['Bảo hành',p=>p.specification?.warrantyMonths?`${p.specification.warrantyMonths} tháng`:null],['Màu xe',p=>p.variants?.map(v=>v.colorName).filter(Boolean).join(', ')]];target.innerHTML=`<div class="compare-scroll"><table class="compare-table"><thead><tr><th>Thông số</th>${products.map(p=>`<th><a href="/product/${encodeURIComponent(p.slug)}"><img src="${escape(p.images?.[0]||'/images/hero-fallback.svg')}" alt=""><b>${escape(p.name)}</b></a><button class="table-action" data-remove-product="${p.id}">Bỏ so sánh</button></th>`).join('')}</tr></thead><tbody>${rows.map(([title,fn])=>`<tr><th>${title}</th>${products.map(p=>`<td>${escape(fn(p)||'—')}</td>`).join('')}</tr>`).join('')}</tbody></table></div>`};
  const renderCheckout=async()=>{
    const items=cart();
    if(!items.length){target.innerHTML='<p class="empty">Gi&#x1ECF; h&#xE0;ng &#x111;ang tr&#x1ED1;ng. <a href="/products">Kh&#xE1;m ph&#xE1; c&#xE1;c m&#x1EAB;u xe &#x2192;</a></p>';finish();return}
    let showrooms=[];
    try{showrooms=await get('/api/v1/showrooms')}catch{}
    const item=items[0];
    target.innerHTML=`<div class="flow-cards"><section class="flow-card"><p class="eyebrow">&#x110;&#x1A0;N H&#xC0;NG</p><h3>${escape(item.name)}</h3><p>${item.colorName?`M&#xE0;u: ${escape(item.colorName)} &#xB7; `:''}S&#x1ED1; l&#x1B0;&#x1EE3;ng: ${item.quantity||1} &#xB7; ${Number(item.price).toLocaleString('vi-VN')} &#x20AB;</p><div id="quote" aria-live="polite"></div></section><form class="flow-form" id="checkoutForm"><label>H&#x1ECD; v&#xE0; t&#xEA;n<input name="customerName" required></label><label>S&#x1ED1; &#x111;i&#x1EC7;n tho&#x1EA1;i<input name="phoneNumber" required></label><label>Email<input name="customerEmail" type="email" required></label><label>C&#x1EED;a h&#xE0;ng nh&#x1EAD;n xe<select name="pickupShowroomId" required><option value="">Ch&#x1ECD;n c&#x1EED;a h&#xE0;ng nh&#x1EAD;n xe</option>${showrooms.map(s=>`<option value="${s.id}">${escape(s.name)} &#x2014; ${escape(s.district)}</option>`).join('')}</select></label><label>&#x110;&#x1ECB;a ch&#x1EC9; nh&#x1EAD;n xe<textarea name="detailedAddress" required></textarea></label><label>Ph&#x1B0;&#x1A1;ng th&#x1EE9;c thanh to&#xE1;n<select name="paymentMethod"><option value="PAY_LATER">Thanh to&#xE1;n khi nh&#x1EAD;n xe</option><option value="VNPAY">VNPay</option></select></label><label>M&#xE3; khuy&#x1EBF;n m&#xE3;i<input name="discountCode" autocomplete="off"><small class="promo-help">M&#xE3; h&#x1EE3;p l&#x1EC7;: EBIKE100 gi&#x1EA3;m 100.000 &#x20AB;, EBIKE200 gi&#x1EA3;m 200.000 &#x20AB;. &#x110;&#x1EC3; tr&#x1ED1;ng n&#x1EBF;u kh&#xF4;ng d&#xF9;ng m&#xE3;.</small></label><label class="check-line"><input type="checkbox" name="includeRegistrationService"> &#x110;&#x103;ng k&#xFD; d&#x1ECB;ch v&#x1EE5; h&#x1ED7; tr&#x1EE3; h&#x1ED3; s&#x1A1;</label><label>Ghi ch&#xFA;<textarea name="notes"></textarea></label><button class="button">&#x110;&#x1EB7;t h&#xE0;ng <b>&#x2197;</b></button></form></div>`;
    finish();

    const form=document.querySelector('#checkoutForm');
    let quoteSequence=0, quoteTimer;
    const money=value=>`${Number(value||0).toLocaleString('vi-VN')} &#x20AB;`;
    const quote=async()=>{
      const sequence=++quoteSequence;
      const box=document.querySelector('#quote');
      const code=form.discountCode.value.trim();
      box.innerHTML='<p class="quote-message">&#x110;ang t&#xED;nh t&#x1ED5;ng ti&#x1EC1;n...</p>';
      try{
        const q=await post('/api/v1/orders/quote',{includeRegistrationService:form.includeRegistrationService.checked,discountCode:code||null,items:items.map(i=>({productId:i.id,quantity:i.quantity||1,productVariantId:i.variantId?Number(i.variantId):null}))});
        if(sequence!==quoteSequence)return;
        const discount=Number(q.discountAmount||0);
        const status=code
          ? `<p class="quote-status success">M&#xE3; <b>${escape(code)}</b> &#x111;&#xE3; &#x111;&#x1B0;&#x1EE3;c &#xE1;p d&#x1EE5;ng.</p>`
          : '<p class="quote-status">Ch&#x1B0;a &#xE1;p d&#x1EE5;ng m&#xE3; khuy&#x1EBF;n m&#xE3;i.</p>';
        box.innerHTML=`<div class="quote-breakdown"><div class="quote-line"><span>T&#x1EA1;m t&#xED;nh</span><strong>${money(q.subtotal)}</strong></div><div class="quote-line"><span>Ph&#xED; giao xe</span><strong>${money(q.shippingFee)}</strong></div><div class="quote-line quote-discount"><span>Gi&#x1EA3;m gi&#xE1; khuy&#x1EBF;n m&#xE3;i</span><strong>${discount?`&minus; ${money(discount)}`:money(0)}</strong></div><div class="quote-line"><span>Ph&#xED; h&#x1ED3; s&#x1A1;</span><strong>${money(q.registrationFee)}</strong></div><div class="quote-line quote-total"><span>T&#x1ED5;ng thanh to&#xE1;n</span><strong>${money(q.totalAmount)}</strong></div>${status}</div>`;
      }catch(err){
        if(sequence!==quoteSequence)return;
        let message=err.message||'Unable to calculate the order total.';
        try{const body=JSON.parse(message);message=body.detail||body.message||message}catch{}
        box.innerHTML=`<p class="quote-message error">${escape(message)}</p>`;
      }
    };
    form.discountCode.addEventListener('input',()=>{quoteSequence++;clearTimeout(quoteTimer);quoteTimer=setTimeout(quote,350)});
    form.addEventListener('change',event=>{if(event.target!==form.discountCode)quote()});
    quote();
    form.addEventListener('submit',async e=>{e.preventDefault();const d=Object.fromEntries(new FormData(form));try{const order=await post('/api/v1/orders',{customerName:d.customerName,phoneNumber:d.phoneNumber,customerEmail:d.customerEmail,pickupShowroomId:d.pickupShowroomId?Number(d.pickupShowroomId):null,detailedAddress:d.detailedAddress,paymentMethod:d.paymentMethod,discountCode:d.discountCode||null,includeRegistrationService:form.includeRegistrationService.checked,notes:d.notes,items:items.map(i=>({productId:i.id,quantity:i.quantity||1,productVariantId:i.variantId?Number(i.variantId):null}))});localStorage.removeItem('kinetic_cart');localStorage.setItem('kinetic_last_order',JSON.stringify(order));location.href='/checkout/success'}catch(err){tell(err.message,true)}});
  };
  const init=async()=>{
    if(flow==='checkout'){await renderCheckout();return}
    if(flow==='checkout-success'){finish();const order=JSON.parse(localStorage.getItem('kinetic_last_order')||'null');target.innerHTML=`<div class="empty"><h2>Cảm ơn bạn đã chọn KINETIC.</h2><p>${order?`Mã đơn hàng: <b>${escape(order.orderNumber)}</b>`:'Đơn hàng đã được ghi nhận.'}</p><a class="button compact" href="/products">Tiếp tục mua sắm ↗</a></div>`;return}
    if(flow==='chatbot'){finish();target.innerHTML='<div id="chatlog" class="chatlog"></div><form class="flow-form" id="chatForm"><label>Đặt câu hỏi<textarea name="message" required placeholder="Bạn tìm xe cho nhu cầu nào?"></textarea></label><button class="button compact">Gửi câu hỏi ↗</button></form>';document.querySelector('#chatForm').onsubmit=async e=>{e.preventDefault();const f=e.currentTarget,log=document.querySelector('#chatlog');log.innerHTML+=`<p class="chat-user">${escape(f.message.value)}</p>`;try{const r=await post('/api/v1/chatbot/ask',{message:f.message.value});log.innerHTML+=`<p class="chat-answer">${escape(r.answer||r.message||JSON.stringify(r))}</p>`;f.reset()}catch(err){tell(err.message,true)}};return}
    if(flow==='compare'){let ids=[];try{ids=JSON.parse(localStorage.getItem('kinetic_compare')||'[]')}catch{}if(!ids.length){renderProducts([],'Chưa có mẫu xe để so sánh. Thêm xe từ danh mục sản phẩm.');return}const all=await get('/api/v1/products');const selected=all.filter(p=>ids.includes(p.id)||ids.includes(String(p.id))||ids.includes(p.slug));const detail=await Promise.all(selected.map(p=>get(`/api/v1/products/${encodeURIComponent(p.slug)}`)));renderCompare(detail);return}
    if(flow==='favorites'){try{const data=await get(api);renderProducts(data||[],'Bạn chưa lưu mẫu xe yêu thích nào. Hãy khám phá cửa hàng để chọn xe.')}catch(e){tell(e.message,true);finish()}return}
    if(flow==='support'){try{const rows=await get(api);finish();if(!rows?.length){target.innerHTML='<p class="empty">Chưa có cửa hàng nhận xe.</p>';return}target.innerHTML=`<div class="flow-cards">${rows.map(s=>`<article class="flow-card"><p class="eyebrow">KINETIC / SHOWROOM</p><h3>${escape(s.name)}</h3><p>${escape(s.address||'')}${s.district?` · ${escape(s.district)}`:''}</p><p>${escape(s.phone||'')}</p></article>`).join('')}</div>`}catch(e){tell(e.message,true);finish()}return}
    if(flow==='payment-return'){finish();target.innerHTML='<div class="empty"><h2>Đang xác nhận kết quả thanh toán</h2><p>Vui lòng vào đơn hàng để kiểm tra trạng thái mới nhất.</p><a class="button compact" href="/customer/orders">Đơn hàng của tôi ↗</a></div>';return}
    finish();
  };
  init().catch(e=>tell(e.message,true));
})();
