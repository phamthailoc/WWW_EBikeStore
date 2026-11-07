(() => {
  'use strict';
  const C=window.KineticCart;
  const {escape:e,money,request,safeImage,key}=C;
  const empty=()=>'<div class="commerce-empty"><span class="empty-bag" aria-hidden="true">＋</span><h2>Giỏ hàng đang trống</h2><p>Chọn mẫu xe và màu bạn yêu thích để bắt đầu hành trình.</p><a class="commerce-button" href="/products">Khám phá các mẫu xe</a></div>';
  const line=item=>`<div class="checkout-item"><img src="${e(safeImage(item.image))}" alt="${e(item.name)}"><div><a href="/product/${encodeURIComponent(item.slug)}">${e(item.name)}</a><small>${e(item.colorName||'Màu tiêu chuẩn')} · ${item.quantity} xe</small><strong>${money(item.price*item.quantity)}</strong></div></div>`;
  const validItems=items=>items.length>0 && items.every(i=>!i.unavailable && i.quantity<=C.limit(i) && C.limit(i)>0);
  const vnpayEnabled=document.body.dataset.vnpayEnabled==='true';
  async function startVnPay(orderId){const payment=await request('/api/v1/payments/vnpay/create',{orderId,language:'vn'});if(!payment?.paymentUrl)throw Error('VNPay không trả về đường dẫn thanh toán.');location.href=payment.paymentUrl;}
  const normalizeSearch=value=>value.normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/đ/g,'d').toLowerCase();

  async function renderCart(target,finish,tell){
    let items=C.read();
    finish();
    if(!items.length){target.innerHTML=empty();return;}
    target.innerHTML='<p class="loading">Đang cập nhật giá và tồn kho...</p>';
    items=await C.refresh(items); C.write(items);
    let rendering=false;
    function render(){
      if(rendering)return;
      rendering=true;
      try{
      if(!items.length){target.innerHTML=empty();return;}
      const valid=validItems(items);
      target.innerHTML=`<div class="commerce-layout"><section class="commerce-card cart-products"><div class="commerce-card-heading"><h2>Sản phẩm đã chọn</h2><span>${items.length} mẫu / màu xe</span></div>${items.map(item=>`<article class="cart-item" data-cart-key="${key(item)}"><a class="cart-item-photo" href="/product/${encodeURIComponent(item.slug)}"><img src="${e(safeImage(item.image))}" alt="${e(item.name)}"></a><div class="cart-item-info"><a href="/product/${encodeURIComponent(item.slug)}"><h3>${e(item.name)}</h3></a><p>Màu: ${e(item.colorName||'Tiêu chuẩn')}</p><strong>${money(item.price)}</strong><div class="quantity-control"><button type="button" data-quantity="minus" aria-label="Giảm số lượng ${e(item.name)}" ${item.quantity<=1?'disabled':''}>−</button><input type="number" class="cart-quantity" aria-label="Số lượng ${e(item.name)}" min="1" max="${Math.max(1,C.limit(item))}" value="${item.quantity}"><button type="button" data-quantity="plus" aria-label="Tăng số lượng ${e(item.name)}" ${item.quantity>=C.limit(item)?'disabled':''}>+</button></div>${item.unavailable||item.quantity>C.limit(item)||!C.limit(item)?'<p class="commerce-error">Xe/màu không đủ tồn kho. Vui lòng giảm số lượng hoặc xóa sản phẩm.</p>':''}</div><div class="cart-item-total"><strong>${money(item.price*item.quantity)}</strong><button type="button" class="text-button remove-cart-item" aria-label="Xóa ${e(item.name)}">Xóa</button></div></article>`).join('')}<a class="continue-shopping" href="/products">← Tiếp tục mua sắm</a></section><aside class="commerce-card cart-summary"><h2>Tóm tắt giỏ hàng</h2><div class="total-line"><span>Số lượng</span><strong>${items.reduce((sum,i)=>sum+i.quantity,0)} xe</strong></div><div class="total-line grand-total"><span>Tạm tính</span><strong>${money(items.reduce((sum,i)=>sum+i.price*i.quantity,0))}</strong></div><p>Mã khuyến mãi và dịch vụ hồ sơ sẽ được tính ở bước thanh toán.</p><button id="checkoutCart" class="commerce-button" ${valid?'':'disabled'}>Tiến hành thanh toán →</button>${!valid?'<p class="commerce-error">Kiểm tra lại các sản phẩm trong giỏ trước khi thanh toán.</p>':''}</aside></div>`;
      document.querySelector('#checkoutCart').onclick=()=>location.href='/checkout';
      }finally{rendering=false;}
    }
    function changeItem(event,value){
      if(rendering)return;
      const row=event.target.closest('[data-cart-key]');
      if(!row)return;
      const item=items.find(i=>key(i)===row.dataset.cartKey);
      if(!item)return;
      const max=C.limit(item);
      if(!Number.isInteger(value)||value<1||value>max){tell(`Số lượng phải từ 1 đến ${max||1}, theo tồn kho của màu xe.`,true);render();return;}
      item.quantity=value; C.write(items); render();
    }
    target.addEventListener('click',event=>{
      const row=event.target.closest('[data-cart-key]');if(!row)return;
      const item=items.find(i=>key(i)===row.dataset.cartKey);
      if(event.target.closest('.remove-cart-item')){items=items.filter(i=>key(i)!==row.dataset.cartKey);C.write(items);render();}
      const button=event.target.closest('[data-quantity]');
      if(button)changeItem(event,item.quantity+(button.dataset.quantity==='plus'?1:-1));
    });
    target.addEventListener('change',event=>{if(event.target.matches('.cart-quantity'))changeItem(event,Number(event.target.value));});
    render();
  }

  async function renderCheckout(target,finish,tell){
    const buyNow=new URLSearchParams(location.search).get('buyNow')==='1';
    let items;
    try{items=buyNow?JSON.parse(sessionStorage.getItem('kinetic_buy_now')||'[]'):C.read();}catch{items=[];}
    if(!Array.isArray(items)||!items.length){finish();target.innerHTML=empty();return;}
    target.innerHTML='<p class="loading">Đang chuẩn bị đơn hàng...</p>';finish();
    const [refreshed,showrooms,provinces]=await Promise.all([C.refresh(items),request('/api/v1/showrooms'),request('/data/vietnam-provinces.json')]);
    items=refreshed;
    if(!validItems(items)){target.innerHTML='<div class="commerce-empty"><h2>Cần kiểm tra lại giỏ hàng</h2><p>Một số xe hoặc màu không còn đủ số lượng bạn chọn.</p><a class="commerce-button" href="/cart">Xem giỏ hàng</a><a href="/products">Chọn lại mẫu xe</a></div>';return;}
    if(!buyNow)C.write(items);
    const stores=showrooms.filter(s=>s.active!==false);
    target.innerHTML=`<form id="checkoutForm" class="commerce-layout checkout-layout"><div class="checkout-forms"><section class="commerce-card"><h2>Thông tin người nhận</h2><div class="contact-grid"><label><span class="field-label">Họ và tên <b>*</b></span><input name="customerName" autocomplete="name" maxlength="100" required placeholder="Họ và tên người nhận"></label><label><span class="field-label">Số điện thoại <b>*</b></span><input name="phoneNumber" type="tel" autocomplete="tel" pattern="0[0-9]{9,10}" required placeholder="Số điện thoại liên hệ"></label><label class="wide-field"><span class="field-label">Email <b>*</b></span><input name="customerEmail" type="email" autocomplete="email" required placeholder="Email nhận thông tin đơn hàng"></label></div></section><section class="commerce-card delivery-card"><h2>Yêu cầu nhận hàng</h2><div class="delivery-options"><label><input type="radio" name="deliveryMethod" value="HOME_DELIVERY" checked> Giao hàng tại nhà</label><label><input type="radio" name="deliveryMethod" value="STORE_PICKUP"> Nhận tại cửa hàng</label></div><div id="homeDeliveryFields" class="delivery-fields"><label id="savedAddressRow" hidden><span class="field-label">Địa chỉ đã lưu</span><select id="savedAddress"></select></label><label><span class="field-label">Tỉnh/Thành phố <b>*</b></span><select name="province" required><option value="">Chọn Tỉnh/Thành phố</option>${provinces.map(p=>`<option value="${e(p)}">${e(p)}</option>`).join('')}</select></label><label><span class="field-label">Địa chỉ nhận hàng <b>*</b></span><input name="detailedAddress" autocomplete="street-address" minlength="10" maxlength="500" required placeholder="Số nhà, tên đường, phường/xã"></label></div><div id="storePickupFields" class="delivery-fields" hidden><label class="showroom-search"><span class="sr-only">Tìm cửa hàng nhận xe</span><input id="showroomSearch" type="search" placeholder="Tìm kiếm cửa hàng, địa chỉ..." aria-label="Tìm cửa hàng nhận xe"></label><div id="showroomResults" class="showroom-results" role="group" aria-label="Danh sách cửa hàng"></div></div><label class="seller-note">Ghi chú cho người bán<textarea name="notes" maxlength="1000" placeholder="Ghi chú cho người bán"></textarea></label></section><section class="commerce-card"><h2>Thanh toán và dịch vụ</h2><label>Phương thức thanh toán<select name="paymentMethod"><option value="PAY_LATER">Thanh toán khi nhận xe</option>${vnpayEnabled?'<option value="VNPAY">Thanh toán online qua VNPay</option>':''}</select></label><div id="registrationBlock"><label class="registration-option"><input type="checkbox" name="includeRegistrationService"> Hỗ trợ đăng ký biển số tại cửa hàng <strong id="registrationServicePrice"></strong></label><p class="registration-note" id="registrationNote"></p></div></section></div><aside class="commerce-card checkout-summary"><div class="commerce-card-heading"><h2>Đơn hàng của bạn</h2><a href="${buyNow?'/product/'+encodeURIComponent(items[0].slug):'/cart'}">Chỉnh sửa</a></div>${items.map(line).join('')}<div class="promo-field"><label for="discountCode">Mã khuyến mãi</label><div><input id="discountCode" name="discountCode" autocomplete="off" placeholder="Nhập mã khuyến mãi"><button type="button" id="applyPromo">Áp dụng</button></div></div><div id="checkoutQuote" aria-live="polite"></div><button type="submit" id="placeOrder" class="commerce-button" disabled>Đặt hàng</button><p class="checkout-confirmation">Kiểm tra thông tin và hình thức nhận hàng trước khi đặt đơn.</p><div id="checkoutError" class="commerce-error" role="alert" hidden></div></aside></form>`;
    const form=document.querySelector('#checkoutForm'),submit=form.querySelector('#placeOrder');
    let selectedStore=null,sequence=0,quoteValid=false,submitting=false,redirecting=false;
    function renderStores(){
      const search=normalizeSearch(form.querySelector('#showroomSearch').value);
      const matches=stores.filter(s=>normalizeSearch([s.name,s.city,s.district,s.address].join(' ')).includes(search));
      if(selectedStore&&!matches.some(s=>s.id===selectedStore))selectedStore=null;
      form.querySelector('#showroomResults').innerHTML=matches.length?matches.map(s=>`<label class="showroom-option"><input type="radio" name="pickupShowroomId" value="${s.id}" ${Number(selectedStore)===s.id?'checked':''}><span><strong>${e(s.name)}</strong><small>${e(s.address)} · ${e(s.city)}<br>${e(s.phone||'')} ${s.openingHours?'· '+e(s.openingHours):''}</small></span></label>`).join(''):'<p class="showroom-empty">Không tìm thấy cửa hàng phù hợp.</p>';
    }
    function updateDelivery(){
      const home=form.elements.deliveryMethod.value==='HOME_DELIVERY';
      form.querySelector('#homeDeliveryFields').hidden=!home;
      form.querySelector('#storePickupFields').hidden=home;
      for(const name of ['province','detailedAddress']){form.elements[name].disabled=!home;form.elements[name].required=home;}
      form.querySelectorAll('[name="pickupShowroomId"]').forEach(input=>{input.disabled=home;input.required=!home;});
    }
    renderStores();updateDelivery();
    // A signed-in customer can pick an address from the profile's address book; guests simply do not see this.
    (async()=>{
      try{
        const asJson=url=>fetch(url,{credentials:'same-origin',headers:{Accept:'application/json'}}).then(r=>r.ok?r.json():null);
        const profile=await asJson('/api/v1/auth/profile');
        if(!profile||!profile.userId)return;
        const saved=((await asJson('/api/v1/users/'+profile.userId+'/addresses'))||[]).filter(a=>provinces.includes(a.city));
        if(!saved.length)return;
        const pick=form.querySelector('#savedAddress');
        pick.innerHTML='<option value="">Nhập địa chỉ khác</option>'+saved.map((a,i)=>'<option value="'+i+'">'+e(a.street)+', '+e(a.city)+(a.isDefault?' (mặc định)':'')+'</option>').join('');
        form.querySelector('#savedAddressRow').hidden=false;
        const apply=a=>{
          form.elements.province.value=a?a.city:'';
          form.elements.detailedAddress.value=a?a.street:'';
          form.elements.province.dispatchEvent(new Event('change',{bubbles:true}));
        };
        pick.addEventListener('change',()=>apply(pick.value===''?null:saved[Number(pick.value)]));
        const fallback=saved.findIndex(a=>a.isDefault);
        if(fallback>=0&&!form.elements.province.value&&!form.elements.detailedAddress.value){pick.value=String(fallback);apply(saved[fallback]);}
      }catch(ignored){/* the address book is optional */}
    })();
    form.querySelector('#showroomSearch').addEventListener('input',()=>{renderStores();updateDelivery();});
    form.addEventListener('change',event=>{
      if(event.target.name==='deliveryMethod')updateDelivery();
      if(event.target.name==='pickupShowroomId')selectedStore=Number(event.target.value);
      if(['includeRegistrationService','discountCode','deliveryMethod','province'].includes(event.target.name))updateQuote();
    });
    async function updateQuote(){
      const current=++sequence,box=form.querySelector('#checkoutQuote');
      quoteValid=false;submit.disabled=true;
      box.innerHTML='<p class="quote-status">Đang tính tổng tiền...</p>';
      try{
        const code=form.elements.discountCode.value.trim();
        const awaitingProvince=form.elements.deliveryMethod.value==='HOME_DELIVERY'&&!form.elements.province.value;
        const q=await request('/api/v1/orders/quote',{items:items.map(i=>({productId:i.id,quantity:i.quantity,productVariantId:i.variantId||null})),discountCode:code||null,includeRegistrationService:form.elements.includeRegistrationService.checked,deliveryMethod:form.elements.deliveryMethod.value,province:form.elements.province.value||null});
        if(current!==sequence)return false;
        box.innerHTML=`<div class="quote-breakdown"><div class="quote-line"><span>Tạm tính</span><strong>${money(q.subtotal)}</strong></div><div class="quote-line"><span>Phí giao xe</span><strong>${awaitingProvince?'Chọn tỉnh để tính phí':money(q.shippingFee)}</strong></div><div class="quote-line quote-discount"><span>Giảm giá khuyến mãi</span><strong>${q.discountAmount?'- ':''}${money(q.discountAmount)}</strong></div><div class="quote-line"><span>Phí hồ sơ</span><strong>${money(q.registrationFee)}</strong></div><div class="quote-line quote-total"><span>Tổng thanh toán</span><strong>${money(q.totalAmount)}</strong></div><p class="quote-status">${code?'Đã áp dụng mã '+e(code)+'.':'Chưa áp dụng mã khuyến mãi.'}</p></div>`;
        form.querySelector('#registrationServicePrice').textContent=`+ ${money(q.registrationServicePerBike)}/xe`;
        // The note shows whenever a bike needs a plate; the option only when the store can help (pickup, service on).
        form.querySelector('#registrationBlock').hidden=!q.registrationRequired;
        form.querySelector('.registration-option').hidden=!q.registrationServiceAvailable;
        if(!q.registrationServiceAvailable)form.elements.includeRegistrationService.checked=false;
        const stateFee=`Lệ phí trước bạ và lệ phí cấp biển số là tiền nộp Nhà nước do chủ xe chịu, không tính vào đơn này (lệ phí trước bạ ước tính ${money(q.registrationTaxLow)} – ${money(q.registrationTaxHigh)} cho xe trong đơn).`;
        const selfService='chủ xe tự đăng ký trên VNeID (tài khoản định danh mức 2) với hóa đơn và phiếu kiểm tra chất lượng xuất xưởng cửa hàng giao kèm xe. ';
        const how=form.elements.includeRegistrationService.checked
          ?'Khi bạn đến nhận xe, nhân viên hỗ trợ đăng ký biển số. Chủ xe cần có mặt và có tài khoản VNeID mức 2 để tự kê khai. Khoản cộng thêm ở trên là phí dịch vụ của cửa hàng. '
          :form.elements.deliveryMethod.value==='HOME_DELIVERY'
            ?'Giao tận nhà: '+selfService+'Cửa hàng chỉ hỗ trợ đăng ký khi bạn nhận xe tại cửa hàng. '
            :(q.registrationServiceAvailable?'Nếu không chọn, ':'Xe cần đăng ký biển số: ')+selfService;
        form.querySelector('#registrationNote').textContent=how+stateFee;
        quoteValid=true;submit.disabled=submitting;return true;
      }catch(error){if(current===sequence)box.innerHTML=`<p class="commerce-error">${e(error.message)}</p>`;return false;}
    }
    form.elements.discountCode.addEventListener('input',()=>{sequence++;quoteValid=false;submit.disabled=true;});
    form.querySelector('#applyPromo').onclick=updateQuote;
    await updateQuote();
    form.addEventListener('submit',async event=>{
      event.preventDefault();if(submitting||!form.reportValidity())return;
      const home=form.elements.deliveryMethod.value==='HOME_DELIVERY';
      const errorBox=form.querySelector('#checkoutError');errorBox.hidden=true;
      if(!home && (!selectedStore || !stores.some(s=>s.id===selectedStore))){errorBox.textContent='Vui lòng chọn cửa hàng nhận xe.';errorBox.hidden=false;return;}
      submitting=true;submit.disabled=true;
      try{
        if(!await updateQuote())return;
        const order=await request('/api/v1/orders',{customerName:form.elements.customerName.value.trim(),phoneNumber:form.elements.phoneNumber.value.trim(),customerEmail:form.elements.customerEmail.value.trim(),deliveryMethod:form.elements.deliveryMethod.value,province:home?form.elements.province.value:null,pickupShowroomId:home?null:selectedStore,detailedAddress:home?form.elements.detailedAddress.value.trim():null,paymentMethod:form.elements.paymentMethod.value,discountCode:form.elements.discountCode.value.trim()||null,includeRegistrationService:form.elements.includeRegistrationService.checked,notes:form.elements.notes.value.trim(),items:items.map(i=>({productId:i.id,quantity:i.quantity,productVariantId:i.variantId||null}))});
        if(buyNow)sessionStorage.removeItem('kinetic_buy_now');
        else C.write(C.read().flatMap(item=>{const purchased=items.find(i=>key(i)===key(item));return purchased?(item.quantity>purchased.quantity?[{...item,quantity:item.quantity-purchased.quantity}]:[]):[item];}));
        if(form.elements.paymentMethod.value==='VNPAY'){
          const pending={...order,checkoutPaymentMethod:'VNPAY'};
          localStorage.setItem('kinetic_last_order',JSON.stringify(pending));
          try{redirecting=true;await startVnPay(order.id);return;}
          catch(error){redirecting=false;localStorage.setItem('kinetic_last_order',JSON.stringify({...pending,vnpayError:error.message}));}
        }else localStorage.setItem('kinetic_last_order',JSON.stringify(order));
        location.href='/checkout/success';
      }catch(error){errorBox.textContent=error.message;errorBox.hidden=false;}
      finally{if(!redirecting){submitting=false;submit.disabled=!quoteValid;}}
    });
  }
  function renderSuccess(target,finish){
    finish();let order=null;try{order=JSON.parse(localStorage.getItem('kinetic_last_order')||'null')}catch{}
    if(!order){target.innerHTML='<div class="commerce-empty"><h2>Chưa có đơn hàng mới</h2><a class="commerce-button" href="/products">Về cửa hàng</a></div>';return;}
    const shipment=order.shipment;
    const awaitingVnPay=order.checkoutPaymentMethod==='VNPAY'&&order.paymentStatus!=='PAID';
    const intro=awaitingVnPay
      ?`<p>Đơn hàng đã được tạo nhưng chưa thanh toán. Bấm nút bên dưới để thanh toán qua VNPay.</p>${order.vnpayError?`<p class="commerce-error" id="vnpayError">Chưa mở được trang VNPay: ${e(order.vnpayError)}</p>`:'<p class="commerce-error" id="vnpayError" hidden></p>'}<p><button type="button" class="commerce-button" id="payVnPay">Thanh toán qua VNPay</button></p>`
      :'<p>Cảm ơn bạn đã chọn KINETIC. Đơn hàng đang chờ xác nhận.</p>';
    target.innerHTML=`<section class="commerce-card order-success"><div class="success-check" aria-hidden="true">✓</div><h2>Đặt hàng thành công</h2>${intro}<strong class="order-number">${e(order.orderNumber)}</strong><div class="order-success-details"><p><span>Nhận hàng</span><strong>${shipment?.pickupShowroom?'Nhận tại cửa hàng':'Giao hàng tại nhà'}</strong></p><p><span>Người nhận</span><strong>${e(shipment?.recipientName||'')}</strong></p><p><span>Địa chỉ</span><strong>${e(shipment?.shippingAddress||'')}</strong></p>${order.items.map(item=>`<p><span>${e(item.productName)} · ${e(item.colorName||'')} × ${item.quantity}</span><strong>${money(item.lineTotal)}</strong></p>`).join('')}<p><span>Tổng thanh toán</span><strong>${money(order.totalAmount)}</strong></p></div><div class="success-actions"><a class="commerce-button" href="/products">Tiếp tục mua sắm</a><a href="/cart">Xem giỏ hàng</a></div></section>`;
    const payButton=target.querySelector('#payVnPay');
    payButton?.addEventListener('click',async()=>{
      const errorBox=target.querySelector('#vnpayError');errorBox.hidden=true;payButton.disabled=true;
      try{await startVnPay(order.id);}
      catch(error){errorBox.textContent=`Chưa mở được trang VNPay: ${error.message}`;errorBox.hidden=false;payButton.disabled=false;}
    });
  }
  window.KineticCommerce={init:async(flow,target,finish,tell)=>{
    document.body.classList.add('commerce-page');
    document.querySelector('#data-table')?.remove();
    document.querySelector('#empty').hidden=true;
    try{if(flow==='cart')await renderCart(target,finish,tell);else if(flow==='checkout')await renderCheckout(target,finish,tell);else renderSuccess(target,finish);}
    catch(error){finish();tell(error.message,true);target.innerHTML='<div class="commerce-empty"><p>Chưa thể tải thông tin. Vui lòng tải lại trang.</p><a href="/cart">Về giỏ hàng</a></div>';}
  }};
})();
