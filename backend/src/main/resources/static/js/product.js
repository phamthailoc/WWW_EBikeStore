(() => {
  'use strict';
  const root = document.querySelector('#productPage');
  if (!root) return;
  const cart = window.KineticCart, quantity = document.querySelector('#productQuantity');
  const notice = document.querySelector('#productNotice');
  const baseImage = root.dataset.image;
  // Colours this customer already waits for (stays empty for guests and staff); drives the label of #notifyStock.
  const waiting = new Set();
  const notifyLabel = () => {
    const button = document.querySelector('#notifyStock');
    if (!button) return;
    if (!button.dataset.label) button.dataset.label = button.textContent.trim();
    button.textContent = waiting.has(item.variantId) ? t('Đã đăng ký báo hàng · bấm để hủy') : button.dataset.label;
  };
  const gallery = document.querySelector('#productGallery');
  const item = {id:Number(root.dataset.id), name:root.dataset.name, price:Number(root.dataset.price), slug:root.dataset.slug, image:baseImage, quantity:1, stock:Number(root.dataset.stock)};
  function message(text, error=false) {
    notice.hidden=false; notice.classList.toggle('error', error); notice.textContent=text;
  }
  function updateQuantity(value) {
    const max=cart.limit(item);
    quantity.max=max; quantity.value=Math.min(Math.max(1, Number(value) || 1), Math.max(1,max));
    item.quantity=Number(quantity.value);
    document.querySelector('#addToCart').disabled=max < 1;
    document.querySelector('#buyNow').disabled=max < 1;
    document.querySelector('#quantityMinus').disabled=item.quantity <= 1;
    document.querySelector('#quantityPlus').disabled=item.quantity >= max;
    const notify=document.querySelector('#notifyStock');
    if(notify){notify.hidden=!(item.variantId&&item.stock<1);notifyLabel();}
    document.querySelector('#stockHint').textContent=max ? `${t('Còn')} ${item.stock} ${t('xe · Tối đa')} ${max} ${t('xe mỗi đơn')}` : t('Màu xe này đã hết hàng');
  }
  function pickVariant(button) {
    document.querySelectorAll('.variant-option').forEach(node => {node.classList.remove('selected'); node.setAttribute('aria-pressed','false');});
    button.classList.add('selected'); button.setAttribute('aria-pressed','true');
    item.variantId=Number(button.dataset.variantId); item.colorName=button.dataset.color;
    item.stock=Math.min(Number(root.dataset.stock), Number(button.dataset.stock));
    item.price=Number(button.dataset.price); item.image=button.dataset.image || baseImage;
    document.querySelector('#selectedColor').textContent=item.colorName;
    const photos=(button.dataset.images||button.dataset.image||'').split('|').filter(Boolean);
    function showPhoto(index){
      const image=document.querySelector('#productImage');
      image.src=cart.safeImage(photos[index]);
      image.alt=`${item.name} - ${item.colorName} ${t('- Ảnh')} ${index+1}`;
      document.querySelector('#photoCaption').textContent=`${item.colorName} ${t('· Ảnh')} ${index+1}/${photos.length}`;
      gallery.querySelectorAll('button').forEach((node,i)=>{node.classList.toggle('selected',i===index);node.setAttribute('aria-pressed',String(i===index));});
    }
    gallery.replaceChildren();
    photos.forEach((photo,index)=>{
      const thumbnail=document.createElement('button');thumbnail.type='button';
      thumbnail.setAttribute('aria-label',`${t('Xem ảnh')} ${index+1} ${t('màu')} ${item.colorName}`);
      const image=document.createElement('img');image.src=cart.safeImage(photo);image.alt='';thumbnail.append(image);
      thumbnail.onclick=()=>showPhoto(index);gallery.append(thumbnail);
    });
    if(photos.length)showPhoto(0);
    document.querySelector('#productPrice').textContent=cart.money(item.price);
    updateQuantity(quantity.value);
  }
  document.querySelectorAll('.variant-option').forEach(button => {
    const tones=(button.dataset.colorHex||'#d6e4d9').split('/');
    button.querySelector('.color-dot').style.background=tones.length>1?`linear-gradient(135deg,${tones[0]} 50%,${tones[1]} 50%)`:tones[0];
    button.addEventListener('click', () => pickVariant(button));
  });
  const selected=document.querySelector('.variant-option.selected');
  if(selected) pickVariant(selected); else {
    updateQuantity(1);document.querySelector('#addToCart').disabled=true;document.querySelector('#buyNow').disabled=true;
    document.querySelector('#stockHint').textContent=t('Màu xe và hình ảnh đang được cập nhật.');
  }
  document.querySelector('#quantityMinus').onclick=()=>updateQuantity(item.quantity-1);
  document.querySelector('#quantityPlus').onclick=()=>updateQuantity(item.quantity+1);
  quantity.addEventListener('change',()=>updateQuantity(quantity.value));
  document.querySelector('#addToCart').onclick=()=>{
    updateQuantity(quantity.value);
    try{cart.add(item); message(`${t('Đã thêm')} ${tBikes(item.quantity)} ${item.name}${item.colorName ? ' · '+item.colorName : ''} ${t('vào giỏ hàng.')}`);}catch(error){message(error.message,true);}
  };
  document.querySelector('#buyNow').onclick=()=>{
    updateQuantity(quantity.value);
    if(cart.limit(item)<1)return;
    sessionStorage.setItem('kinetic_buy_now',JSON.stringify([{...item}]));
    location.href='/checkout?buyNow=1';
  };
  document.querySelector('#addFavorite').onclick=async()=>{
    try{await cart.request('/api/v1/favorites',{productId:item.id});message(t('Đã thêm xe vào danh sách yêu thích.'));}
    catch(error){if(/Unauthorized|401|đăng nhập|sign in/i.test(error.message)){location.href='/login?returnTo='+encodeURIComponent(location.pathname);return;}message(error.message,true);}
  };
  const notifyButton=document.querySelector('#notifyStock');
  const goToLogin=variantId=>{location.href='/login?returnTo='+encodeURIComponent(location.pathname+'?notify='+variantId);};
  const sendNotify=async(auto)=>{
    if(!notifyButton||notifyButton.disabled)return;
    notifyButton.disabled=true;
    const variantId=item.variantId;
    try{
      if(waiting.has(variantId)){
        const response=await fetch(`/api/v1/stock-alerts/${variantId}`,{method:'DELETE',credentials:'same-origin'});
        if(response.status===401){if(!auto)goToLogin(variantId);return;}
        if(response.status===403){message(t('Chỉ tài khoản khách hàng mới đăng ký báo hàng được. Hãy đăng nhập bằng tài khoản khách hàng.'),true);return;}
        if(!response.ok&&response.status!==204){
          const body=await response.json().catch(()=>null);
          throw Error(body?.message||'Không hủy được đăng ký báo hàng. Vui lòng thử lại.');
        }
        waiting.delete(variantId);message(t('Đã hủy đăng ký báo hàng.'));
      }else{
        const saved=await cart.request('/api/v1/stock-alerts',{variantId});
        if(saved&&(saved.notifiedAt||saved.stockQuantity>0)){
          // The colour came back in stock just now and the server already told the customer: show it as available, not as waiting.
          const option=document.querySelector(`.variant-option[data-variant-id="${variantId}"]`);
          if(option&&saved.stockQuantity>0){
            option.dataset.stock=String(saved.stockQuantity);
            root.dataset.stock=String(Math.max(Number(root.dataset.stock),saved.stockQuantity));
            pickVariant(option);
          }
          message(saved.stockQuantity>0?t('Màu xe này đang còn hàng, bạn có thể đặt mua ngay.'):t('Màu xe này vừa có hàng lại nhưng đã hết ngay. Bạn có thể bấm lại để đăng ký báo hàng.'));
          return;
        }
        waiting.add(variantId);message(t('Đã đăng ký. Chúng tôi sẽ báo khi màu này có hàng lại.'));
      }
      notifyLabel();
    }
    catch(error){
      if(/Unauthorized|401|đăng nhập|sign in/i.test(error.message)){if(!auto)goToLogin(variantId);return;}
      // 403: a staff account (Manager, Admin...) is signed in; only customers can ask to be told.
      if(/\b403\b|Forbidden|permission|quyền/i.test(error.message)){message(t('Chỉ tài khoản khách hàng mới đăng ký báo hàng được. Hãy đăng nhập bằng tài khoản khách hàng.'),true);}
      else message(t(error.message),true);
    }
    finally{notifyButton.disabled=false;}
  };
  const isCustomer=root.dataset.customer==='true';
  // Back from the login page after pressing the button as a guest: finish what the customer asked for, or say why it cannot be done.
  const finishWanted=()=>{
    const wanted=Number(new URLSearchParams(location.search).get('notify'));
    if(!wanted)return;
    try{history.replaceState(null,'',location.pathname);}catch{}
    const option=document.querySelector(`.variant-option[data-variant-id="${wanted}"]`);
    if(!option){message(t('Không tìm thấy màu xe này.'),true);return;}
    pickVariant(option);
    if(!notifyButton){message(t('Chỉ tài khoản khách hàng mới đăng ký báo hàng được. Hãy đăng nhập bằng tài khoản khách hàng.'),true);return;}
    if(!isCustomer)return; // still a guest: nothing was asked for
    if(notifyButton.hidden){message(t('Màu xe này đang còn hàng, bạn có thể đặt mua ngay.'));return;}
    if(!waiting.has(wanted))sendNotify(true);
  };
  if(notifyButton)notifyButton.onclick=()=>sendNotify(false);
  // Which colours does this customer already wait for? Only for a signed-in customer and only when a colour is sold out;
  // guests and staff would just get 401/403.
  const soldOutColour=[...document.querySelectorAll('.variant-option')].some(o=>Number(o.dataset.stock)<1);
  if(notifyButton&&isCustomer&&soldOutColour){
    fetch('/api/v1/stock-alerts',{credentials:'same-origin',headers:{Accept:'application/json'}})
      .then(r=>r.ok?r.json():[])
      .then(rows=>{(rows||[]).forEach(r=>{if(!r.notifiedAt&&r.stockQuantity<1)waiting.add(r.variantId);});notifyLabel();})
      .catch(()=>{})
      .then(finishWanted);
  }else finishWanted();
  document.querySelector('#addCompare').onclick=()=>{
    let ids=[];try{ids=JSON.parse(localStorage.getItem('kinetic_compare')||'[]')}catch{}
    if(!ids.includes(item.id))ids.push(item.id);
    localStorage.setItem('kinetic_compare',JSON.stringify(ids.slice(0,4)));location.href='/compare';
  };
})();
