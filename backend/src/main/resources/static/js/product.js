(() => {
  'use strict';
  const root = document.querySelector('#productPage');
  if (!root) return;
  const cart = window.KineticCart, quantity = document.querySelector('#productQuantity');
  const notice = document.querySelector('#productNotice');
  const baseImage = root.dataset.image;
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
    catch(error){if(/Unauthorized|401/i.test(error.message)){location.href='/login?returnTo='+encodeURIComponent(location.pathname);return;}message(error.message,true);}
  };
  document.querySelector('#addCompare').onclick=()=>{
    let ids=[];try{ids=JSON.parse(localStorage.getItem('kinetic_compare')||'[]')}catch{}
    if(!ids.includes(item.id))ids.push(item.id);
    localStorage.setItem('kinetic_compare',JSON.stringify(ids.slice(0,4)));location.href='/compare';
  };
})();
