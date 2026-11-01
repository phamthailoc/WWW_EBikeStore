(() => {
  'use strict';
  const root = document.querySelector('#productPage');
  if (!root) return;
  const cart = window.KineticCart, quantity = document.querySelector('#productQuantity');
  const notice = document.querySelector('#productNotice');
  const baseImage = root.dataset.image;
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
    document.querySelector('#stockHint').textContent=max ? `Còn ${item.stock} xe · Tối đa ${max} xe mỗi đơn` : 'Màu xe này đã hết hàng';
  }
  function pickVariant(button) {
    document.querySelectorAll('.variant-option').forEach(node => {node.classList.remove('selected'); node.setAttribute('aria-pressed','false');});
    button.classList.add('selected'); button.setAttribute('aria-pressed','true');
    item.variantId=Number(button.dataset.variantId); item.colorName=button.dataset.color;
    item.stock=Math.min(Number(root.dataset.stock), Number(button.dataset.stock));
    item.price=Number(button.dataset.price); item.image=button.dataset.image || baseImage;
    document.querySelector('#selectedColor').textContent=item.colorName;
    document.querySelector('#productImage').src=cart.safeImage(item.image);
    document.querySelector('#productPrice').textContent=cart.money(item.price);
    updateQuantity(quantity.value);
  }
  document.querySelectorAll('.variant-option').forEach(button => button.addEventListener('click', () => pickVariant(button)));
  const selected=document.querySelector('.variant-option.selected');
  if(selected) pickVariant(selected); else updateQuantity(1);
  document.querySelector('#quantityMinus').onclick=()=>updateQuantity(item.quantity-1);
  document.querySelector('#quantityPlus').onclick=()=>updateQuantity(item.quantity+1);
  quantity.addEventListener('change',()=>updateQuantity(quantity.value));
  document.querySelector('#addToCart').onclick=()=>{
    updateQuantity(quantity.value);
    try{cart.add(item); message(`Đã thêm ${item.quantity} xe ${item.name}${item.colorName ? ' · '+item.colorName : ''} vào giỏ hàng.`);}catch(error){message(error.message,true);}
  };
  document.querySelector('#buyNow').onclick=()=>{
    updateQuantity(quantity.value);
    if(cart.limit(item)<1)return;
    sessionStorage.setItem('kinetic_buy_now',JSON.stringify([{...item}]));
    location.href='/checkout?buyNow=1';
  };
  document.querySelector('#addFavorite').onclick=async()=>{
    try{await cart.request('/api/v1/favorites',{productId:item.id});message('Đã thêm xe vào danh sách yêu thích.');}
    catch(error){if(/Unauthorized|401/i.test(error.message)){location.href='/login?returnTo='+encodeURIComponent(location.pathname);return;}message(error.message,true);}
  };
  document.querySelector('#addCompare').onclick=()=>{
    let ids=[];try{ids=JSON.parse(localStorage.getItem('kinetic_compare')||'[]')}catch{}
    if(!ids.includes(item.id))ids.push(item.id);
    localStorage.setItem('kinetic_compare',JSON.stringify(ids.slice(0,4)));location.href='/compare';
  };
})();
