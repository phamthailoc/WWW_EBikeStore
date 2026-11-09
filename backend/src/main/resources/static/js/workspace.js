(() => {
  const body = document.body, page = body.dataset.page || '', api = body.dataset.api || '', secondaryApi = body.dataset.secondaryApi || '', mode = body.dataset.mode || 'table';
  const notice = document.querySelector('#notice'), loading = document.querySelector('#loading'), empty = document.querySelector('#empty'), table = document.querySelector('#data-table'), metrics = document.querySelector('#metrics'), profile = document.querySelector('#profile');
  let promotions = [], records = [];
  const names = {customerName:'Khách hàng',subject:'Tiêu đề',message:'Nội dung',staffNote:'Ghi chú xử lý',stockStatus:'Tình trạng kho',paymentStatus:'Trạng thái thanh toán',role:'Mã vai trò',title:'Vai trò',permissions:'Quyền',actor:'Người thực hiện',action:'Hành động',target:'Đối tượng',createdAt:'Thời gian',id:'Mã',name:'Tên',slug:'Đường dẫn',description:'Mô tả',price:'Giá',discountPrice:'Giá ưu đãi',rating:'Đánh giá',reviewCount:'Lượt đánh giá',stockQuantity:'Tồn kho',featured:'Nổi bật',category:'Danh mục',images:'Hình ảnh',orderId:'Mã đơn hàng',orderNumber:'Mã đơn hàng',status:'Trạng thái',createdAt:'Ngày tạo',updatedAt:'Cập nhật',totalAmount:'Tổng tiền',paymentMethod:'Phương thức thanh toán',paymentStatus:'Trạng thái thanh toán',customerName:'Khách hàng',customerEmail:'Email khách hàng',email:'Email',phone:'Điện thoại',phoneNumber:'Điện thoại',role:'Vai trò',roles:'Vai trò',username:'Tài khoản',accountType:'Loại tài khoản',amount:'Số tiền',code:'Mã ưu đãi',campaignName:'Chiến dịch',discountType:'Loại ưu đãi',discountValue:'Giá trị ưu đãi',startsAt:'Bắt đầu',endsAt:'Kết thúc',totalOrders:'Tổng đơn hàng',successfulOrders:'Đơn hoàn tất',pendingOrders:'Đơn đang chờ',unpaidPayLaterOrders:'Đơn trả sau chưa thu',todayRevenue:'Doanh thu hôm nay',weekRevenue:'Doanh thu tuần',monthRevenue:'Doanh thu tháng',totalRevenue:'Tổng doanh thu',totalAccounts:'Tổng tài khoản',adminAccounts:'Tài khoản quản trị',customerAccounts:'Tài khoản khách hàng',lockedAccounts:'Tài khoản bị khóa',verified:'Đã xác minh',readAt:'Ngày đã đọc',action:'Thao tác',actor:'Người thực hiện',target:'Mục tiêu',ipAddress:'Địa chỉ IP',type:'Loại',message:'Nội dung',title:'Tiêu đề',quantitySold:'Đã bán',productName:'Sản phẩm',unit:'Đơn vị',effectiveFrom:'Có hiệu lực từ',transactionReference:'Mã giao dịch',paidAt:'Ngày thanh toán',subtotal:'Tạm tính',shippingFee:'Phí giao xe',discountAmount:'Giảm giá',registrationFee:'Phí đăng ký',includeRegistrationService:'Dịch vụ hồ sơ',notes:'Ghi chú',shipment:'Giao nhận',label:'Kỳ báo cáo',orderCount:'Số đơn hàng',revenue:'Doanh thu'};
  const label = key => names[key] || key.replace(/([A-Z])/g, ' $1').replace(/^./, c => c.toUpperCase());
  const escape = value => String(value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const orderStatusText={PENDING:'Chờ xử lý',CONFIRMED:'Đã xác nhận',PROCESSING:'Đang xử lý',READY_FOR_PICKUP:'Chờ lấy',SHIPPED:'Đang giao',DELIVERED:'Đã giao',CANCELLED:'Đã hủy'};
  const valueText = {
    orderStatus:orderStatusText,
    paymentStatus:{PENDING:'Chờ thanh toán',AUTHORIZED:'Đã ủy quyền',PAID:'Đã thanh toán',FAILED:'Thất bại',CANCELLED:'Đã hủy',REFUNDED:'Đã hoàn tiền'},
    paymentMethod:{PAY_LATER:'Thanh toán khi nhận xe',VNPAY:'VNPay'},
    status:{...orderStatusText,NEW:'Mới',IN_PROGRESS:'Đang xử lý',RESOLVED:'Đã giải quyết'}
  };
  const human = (value,key='') => {
    if (value == null) return '—';
    if (valueText[key] && valueText[key][value]) return valueText[key][value];
    if (typeof value === 'object') return Array.isArray(value) ? value.map(item=>typeof item==='object'?(item.name||item.colorName||''):String(item)).join(', ') : (value.name || value.colorName || value.label || '');
    if (typeof value === 'boolean') return value ? 'Có' : 'Không';
    if (typeof value === 'number' && /amount|price|revenue|spent|fee/i.test(key)) return new Intl.NumberFormat('vi-VN').format(value) + ' ₫';
    return String(value);
  };
  const tell = (message,error=false) => { notice.textContent=message; notice.hidden=false; notice.classList.toggle('error',error); };
  const get = async url => { const r=await fetch(url,{credentials:'same-origin',headers:{Accept:'application/json'}}); if(r.status===401){location.href='/login?returnTo='+encodeURIComponent(location.pathname);throw Error('Vui lòng đăng nhập để tiếp tục.')} if(!r.ok)throw Error((await r.text()).slice(0,240)||'Không tải được dữ liệu.'); return r.status===204?null:r.json(); };
  const send = async(url,method,data) => { const r=await fetch(url,{method,credentials:'same-origin',headers:{'Content-Type':'application/json',Accept:'application/json'},body:data===undefined?undefined:JSON.stringify(data)});if(r.status===401){location.href='/login';throw Error('Phiên đăng nhập đã hết hạn.')}if(!r.ok)throw Error((await r.text()).slice(0,240)||'Không thể lưu thay đổi.');return r.status===204?null:r.json(); };
  const vnpayEnabled = body.dataset.vnpayEnabled === 'true';
  // VNPay orders have no payment row until the first payment URL is created, so a missing method also means VNPay.
  const awaitsVnPay = row => vnpayEnabled && row.orderNumber != null && row.status !== 'CANCELLED'
    && (row.paymentMethod == null || row.paymentMethod === 'VNPAY') && !['PAID','REFUNDED'].includes(row.paymentStatus);
  const actionFor = row => {
    const id=row.id??row.orderId??row.paymentId??row.productId, buttons=[];
    if(page==='/manager/payments'&&id!=null&&row.paymentMethod==='PAY_LATER'&&row.paymentStatus==='PENDING'&&row.orderStatus!=='CANCELLED')buttons.push(`<button class="table-action" data-action="confirm-payment" data-id="${id}">Xác nhận đã thu</button>`);
    if(page==='/manager/payments'&&id!=null&&row.paymentStatus==='PAID'&&row.orderStatus==='CANCELLED')buttons.push(`<button class="table-action" data-action="refund-payment" data-id="${id}">Ghi nhận hoàn tiền</button>`);
    if(page==='/manager/orders'&&id!=null&&orderSteps(row).length)buttons.push(`<select class="table-action" data-action="order-status" data-id="${id}"><option value="">Cập nhật trạng thái…</option>${orderSteps(row).map(s=>`<option value="${s}">${stepText(s,row)}</option>`).join('')}</select>`);
    if(page.startsWith('/manager')&&id!=null&&(page.includes('inventory')||page.includes('products')))buttons.push(`<button class="table-action" data-action="stock" data-id="${id}" data-slug="${escape(row.slug||'')}" data-stock="${row.stockQuantity??0}">Cập nhật tồn kho</button><button class="table-action" data-action="images" data-id="${id}" data-slug="${escape(row.slug||'')}" data-name="${escape(row.name||'')}">Quản lý ảnh</button>`);
    if(page.startsWith('/admin')&&id!=null&&(page.includes('inventory')||page.includes('products')))buttons.push(`<button class="table-action" data-action="stock" data-id="${id}" data-slug="${escape(row.slug||'')}" data-stock="${row.stockQuantity??0}">Cập nhật tồn kho</button><button class="table-action" data-action="images" data-id="${id}" data-slug="${escape(row.slug||'')}" data-name="${escape(row.name||'')}">Quản lý ảnh</button>`);
    if(page==='/admin/accounts'&&id!=null)buttons.push(`<button class="table-action" data-action="role" data-id="${id}">Đổi vai trò</button><button class="table-action" data-action="account-status" data-id="${id}" data-status="${row.status||'ACTIVE'}">${row.status==='ACTIVE'?'Vô hiệu hóa':'Kích hoạt lại'}</button><button class="table-action" data-action="delete-account" data-id="${id}" data-email="${escape(row.email||'')}">Xóa</button>`);
    if(page==='/admin/pricing'&&id!=null)buttons.push(`<button class="table-action" data-action="pricing" data-id="${id}" data-amount="${row.amount||0}" data-status="${row.status||''}">Sửa quy tắc</button>`);
    if(page==='/manager/support'&&id!=null){if(row.status==='NEW')buttons.push(`<button class="table-action" data-action="support-status" data-id="${id}" data-status="IN_PROGRESS">Nhận xử lý</button>`);if(row.status!=='RESOLVED')buttons.push(`<button class="table-action" data-action="support-status" data-id="${id}" data-status="RESOLVED">Đã giải quyết</button>`);else buttons.push(`<button class="table-action" data-action="support-status" data-id="${id}" data-status="IN_PROGRESS">Mở lại</button>`)}
    if(page==='/customer/notifications'&&id!=null&&!row.readAt)buttons.push(`<button class="table-action" data-action="read" data-id="${id}">Đánh dấu đã đọc</button>`);
    if(page.startsWith('/customer/orders')&&id!=null&&awaitsVnPay(row))buttons.push(`<button class="table-action" data-action="pay-vnpay" data-id="${id}">Thanh toán VNPay</button>`);
    if(page.startsWith('/customer/orders')&&id!=null&&['PENDING','CONFIRMED','PROCESSING'].includes(row.status))buttons.push(`<button class="table-action" data-action="cancel-order" data-id="${id}">Hủy đơn</button>`);
    if(page==='/favorites'&&id!=null)buttons.push(`<a class="table-action" href="/product/${encodeURIComponent(row.slug||'')}">Xem xe</a><button class="table-action" data-action="remove-favorite" data-id="${id}">Bỏ yêu thích</button>`);
    if((page==='/customer/orders'||page==='/manager/orders'||page==='/manager')&&id!=null)buttons.push(`<a class="table-action" href="${page.startsWith('/manager')?'/manager':'/customer'}/orders/${id}">Chi tiết</a>`);
    return buttons.length?`<div class="product-action">${buttons.join('')}</div>`:'';
  };
  const orderSummaryColumns=['orderNumber','status','totalAmount','paymentMethod','paymentStatus','createdAt','updatedAt'];
  const columnsForPage = {
    '/admin/accounts':['id','name','username','email','role','status','verified','createdAt'],
    '/admin/roles':['role','title','description','permissions'],
    '/admin/audit-logs':['createdAt','actor','action','target','ipAddress'],
    '/admin/users':['id','name','username','email','role','status','verified','createdAt'],
    '/admin/pricing':['id','code','name','amount','unit','status','effectiveFrom'],
    '/manager':['id','orderNumber','status','totalAmount','paymentMethod','paymentStatus','createdAt'],
    '/manager/reports':['label','orderCount','revenue'],
    '/manager/orders':['id','orderNumber','status','totalAmount','paymentMethod','paymentStatus','customerEmail','createdAt'],
    '/manager/payments':['id','orderNumber','customerName','amount','paymentMethod','paymentStatus','transactionReference','paidAt'],
    '/manager/support':['id','customerName','email','phone','subject','message','status','staffNote','createdAt'],
    '/manager/customers':['userId','customerName','email','phoneNumber','orderCount','totalSpent','lastOrderAt'],
    '/manager/inventory':['id','name','price','stockQuantity','stockStatus','featured'],
    '/manager/products':['id','name','price','stockQuantity','stockStatus','featured'],
    '/admin/inventory':['id','name','price','stockQuantity','stockStatus','featured'],
    '/admin/products':['id','name','price','stockQuantity','stockStatus','featured'],
    '/customer/orders':['id','orderNumber','status','totalAmount','paymentMethod','paymentStatus','createdAt'],
    '/customer/payments':['id','orderNumber','amount','paymentMethod','paymentStatus','createdAt'],
    '/customer/notifications':['id','title','message','type','createdAt','readAt']
  };
  const renderTable = rows => {
    records=Array.isArray(rows)?rows:rows==null?[]:[rows]; loading.hidden=true;
    if(!records.length){empty.hidden=false;table.hidden=true;return}
    empty.hidden=true;table.hidden=false;
    const available=[...new Set(records.flatMap(row=>Object.keys(row||{})))].filter(k=>!['items','variants','permissions','specification','addresses','category','images','shipment'].includes(k));
    const keys=(mode==='detail'?orderSummaryColumns:columnsForPage[page]||available.slice(0,9)).filter(k=>available.includes(k));
    const hasActions=records.some(row=>actionFor(row));
    table.querySelector('thead').innerHTML=`<tr>${keys.map(k=>`<th data-field="${escape(k)}">${escape(label(k))}</th>`).join('')}${hasActions?'<th>Thao tác</th>':''}</tr>`;
    table.querySelector('tbody').innerHTML=records.map(row=>`<tr>${keys.map(k=>`<td data-field="${escape(k)}">${escape(human(row[k],k))}</td>`).join('')}${hasActions?`<td>${actionFor(row)}</td>`:''}</tr>`).join('');
  };
  const renderFavorites = rows => {
    const list=Array.isArray(rows)?rows:[]; loading.hidden=true; empty.hidden=!!list.length; table.hidden=true;
    if(!list.length)return;
    const panel=document.querySelector('#data-panel'); panel.classList.add('favorite-panel');
    panel.innerHTML=`<div class="favorite-grid">${list.map(p=>{const image=p.images?.[0]||'/images/hero-fallback.svg';return `<article class="favorite-card"><a href="/product/${encodeURIComponent(p.slug)}"><img src="${escape(image)}" alt="${escape(p.name)}"><p class="eyebrow">${escape(p.category?.name||'KINETIC SELECT')}</p><h2>${escape(p.name)}</h2><p class="favorite-price">${escape(new Intl.NumberFormat('vi-VN').format(p.discountPrice||p.price))} ₫</p></a><div class="product-action"><a class="button compact ghost" href="/product/${encodeURIComponent(p.slug)}">Xem chi tiết</a><button class="table-action" data-action="remove-favorite" data-id="${p.id}">Bỏ yêu thích</button></div></article>`}).join('')}</div>`;
  };
  const renderPromotions=rows=>{promotions=Array.isArray(rows)?rows:[];const panel=document.querySelector('#promo-panel');if(!panel)return;panel.hidden=false;const t=document.querySelector('#promo-table');if(!promotions.length){t.querySelector('tbody').innerHTML='<tr><td>Chưa có chương trình.</td></tr>';return}const keys=Object.keys(promotions[0]);t.querySelector('thead').innerHTML=`<tr>${keys.map(k=>`<th>${escape(label(k))}</th>`).join('')}<th>Thao tác</th></tr>`;t.querySelector('tbody').innerHTML=promotions.map(p=>`<tr>${keys.map(k=>`<td>${escape(human(p[k],k))}</td>`).join('')}<td><button class="table-action" data-action="promotion" data-id="${p.id}">Sửa ưu đãi</button></td></tr>`).join('')};
  const renderMetrics=data=>{if(!data||typeof data!=='object')return;const entries=Object.entries(data).filter(([k,v])=>typeof v==='number'&&!/id$/i.test(k));metrics.innerHTML=entries.map(([k,v])=>`<article class="metric"><small>${escape(label(k))}</small><b>${escape(human(v,k))}</b></article>`).join('')};
  // Bar chart of revenue per period, drawn as inline SVG (no library); the table below is its text equivalent.
  const renderRevenueChart=points=>{
    let panel=document.querySelector('#report-chart');
    if(!panel){panel=document.createElement('section');panel.id='report-chart';panel.className='data-panel report-chart';document.querySelector('#data-panel').before(panel)}
    if(!points.length||points.every(p=>!Number(p.revenue))){panel.innerHTML='<p class="eyebrow">KINETIC / DOANH THU</p><h2>Biểu đồ doanh thu</h2><p>Chưa có doanh thu trong khoảng thời gian này.</p>';return}
    const W=720,H=260,left=64,bottom=44,top=16,right=12,plotW=W-left-right,plotH=H-top-bottom;
    const max=Math.max(...points.map(p=>Number(p.revenue)||0));
    const step=Math.pow(10,Math.floor(Math.log10(max)));const niceMax=Math.ceil(max/step)*step;
    const slot=plotW/points.length,barW=Math.min(48,slot*0.64);
    const short=v=>v>=1e9?(v/1e9).toFixed(1).replace('.0','')+' tỷ':v>=1e6?(v/1e6).toFixed(1).replace('.0','')+' tr':new Intl.NumberFormat('vi-VN').format(v);
    const grid=[0,0.5,1].map(t=>{const y=top+plotH*(1-t);return `<line x1="${left}" x2="${W-right}" y1="${y}" y2="${y}" class="grid"/><text x="${left-8}" y="${y+4}" text-anchor="end">${short(niceMax*t)}</text>`}).join('');
    const bars=points.map((p,i)=>{const v=Number(p.revenue)||0,h=plotH*v/niceMax,x=left+slot*i+(slot-barW)/2,y=top+plotH-h;
      const every=Math.ceil(points.length/12);
      return `<g><rect x="${x}" y="${y}" width="${barW}" height="${Math.max(h,0)}" rx="3"><title>${escape(p.label)}: ${escape(human(v,'revenue'))} (${p.orderCount} đơn)</title></rect>${i%every===0?`<text x="${x+barW/2}" y="${H-bottom+18}" text-anchor="middle">${escape(String(p.label).slice(0,10))}</text>`:''}</g>`}).join('');
    const total=points.reduce((sum,p)=>sum+(Number(p.revenue)||0),0);
    panel.innerHTML=`<p class="eyebrow">KINETIC / DOANH THU</p><h2>Biểu đồ doanh thu</h2><svg viewBox="0 0 ${W} ${H}" class="revenue-chart" role="img" aria-label="Biểu đồ cột doanh thu theo kỳ, tổng ${escape(human(total,'revenue'))}">${grid}${bars}</svg>`;
  };
  const reportList=rows=>rows.length?`<ol class="report-list">${rows.map(r=>`<li><span><b>${escape(r.productName||'Sản phẩm')}</b><small>${escape(human(r.quantitySold,'quantitySold'))} xe đã bán</small></span><strong>${escape(human(r.revenue,'revenue'))}</strong></li>`).join('')}</ol>`:'<p class="empty">Chưa có dữ liệu trong kỳ này.</p>';
  const renderProfile=data=>{profile.hidden=false;const fields=['firstName','lastName','email','phone','phoneNumber'];profile.innerHTML=`<form id="profileForm"><p class="eyebrow">THÔNG TIN TÀI KHOẢN</p><div class="profile-grid">${fields.filter(f=>f in(data||{})).map(f=>`<label>${escape(label(f))}<input name="${f}" value="${escape(data[f]||'')}"></label>`).join('')}</div><p><button class="button compact">Lưu hồ sơ</button></p></form>`;profile.querySelector('form').addEventListener('submit',async e=>{e.preventDefault();try{await send('/api/v1/auth/profile','PUT',Object.fromEntries(new FormData(e.currentTarget)));tell('Hồ sơ đã được cập nhật.')}catch(err){tell(err.message,true)}})};
  // ---- order detail page -------------------------------------------------------------
  // Mirrors OrderServiceImpl.STATUS_TRANSITIONS: the steps staff may take from each status.
  // After PROCESSING, store-pickup orders wait in READY_FOR_PICKUP; home deliveries go through SHIPPED.
  const isPickup=order=>Boolean(order?.shipment?.pickupShowroom);
  const orderSteps=order=>{
    const pickup=isPickup(order);
    // A waiting pickup order may only be cancelled once the hold period after notifying the customer is over.
    const holdOver=!order?.pickupDeadlineAt||new Date(order.pickupDeadlineAt)<=new Date();
    return ({PENDING:['CONFIRMED','CANCELLED'],CONFIRMED:['PROCESSING','CANCELLED'],PROCESSING:[pickup?'READY_FOR_PICKUP':'SHIPPED','CANCELLED'],READY_FOR_PICKUP:holdOver?['DELIVERED','CANCELLED']:['DELIVERED'],SHIPPED:['DELIVERED']})[order?.status]||[];
  };
  // The last step reads "Đã nhận" when the customer collects the bike at the showroom.
  const stepText=(status,order)=>status==='DELIVERED'&&isPickup(order)?'Đã nhận':orderStatusText[status];
  const shipmentStatusText={PENDING:'Chưa chuẩn bị',PREPARING:'Đang chuẩn bị',SHIPPED:'Đã bàn giao vận chuyển',IN_TRANSIT:'Đang vận chuyển',DELIVERED:'Đã giao',RETURNED:'Hoàn trả'};
  const money=value=>new Intl.NumberFormat('vi-VN').format(value??0)+' ₫';
  const when=value=>value?new Date(value).toLocaleString('vi-VN'):'—';
  const detailPanel=(id,html)=>{
    let panel=document.querySelector('#'+id);
    if(!panel){panel=document.createElement('section');panel.id=id;panel.className='data-panel';panel.style.marginTop='20px';document.querySelector('.work-main').appendChild(panel);}
    panel.innerHTML=html;
    return panel;
  };
  const removePanel=id=>document.querySelector('#'+id)?.remove();
  // Staff cancellations need a reason, exactly like the customer's. Returns false when nothing was sent.
  const changeOrderStatus=async(id,status)=>{
    const payload={status};
    if(status==='CANCELLED'){
      const reason=prompt('Lý do hủy đơn hàng');
      if(reason===null)return false;
      if(!reason.trim()){tell('Vui lòng nhập lý do hủy đơn hàng.',true);return false}
      payload.reason=reason.trim();
    }
    await send(`/api/v1/orders/${id}/status`,'PATCH',payload);
    return true;
  };
  const renderOrderItems=order=>{
    const items=order.items||[];
    if(!items.length){removePanel('orderItems');return}
    const rows=items.map(item=>`<tr><td>${escape(item.productName||'—')}</td><td>${escape(item.colorName||'—')}</td><td>${escape(item.quantity??0)}</td><td>${escape(money(item.unitPrice))}</td><td>${escape(money(item.lineTotal))}</td></tr>`).join('');
    detailPanel('orderItems',`<h2>Xe trong đơn hàng</h2><div class="table-scroll"><table><thead><tr><th>Xe</th><th>Màu</th><th>Số lượng</th><th>Đơn giá</th><th>Thành tiền</th></tr></thead><tbody>${rows}</tbody></table></div>`);
  };
  const renderPricing=order=>{
    const line=(name,value)=>`<tr><td>${name}</td><td data-price="${escape(name)}">${escape(value)}</td></tr>`;
    const lines=[line('Tạm tính',money(order.subtotal))];
    if(order.includeRegistrationService)lines.push(line('Dịch vụ đăng ký xe',money(order.registrationFee)));
    if(Number(order.shippingFee)>0)lines.push(line('Phí giao hàng',money(order.shippingFee)));
    if(Number(order.discountAmount)>0)lines.push(line('Giảm giá','−'+money(order.discountAmount)));
    lines.push(line('<strong>Tổng cộng</strong>',money(order.totalAmount)));
    const payment=`${order.paymentMethod||'—'} · ${order.paymentStatus||'Chưa có giao dịch'}`;
    detailPanel('orderPricing',`<h2>Giá và thanh toán</h2><div class="table-scroll"><table><tbody>${lines.join('')}</tbody></table></div><p><strong>Thanh toán:</strong> ${escape(payment)}</p>${order.notes?`<p><strong>Ghi chú:</strong> ${escape(order.notes)}</p>`:''}`);
  };
  // Order timeline (Task 3). Only three moments are stored: the order date, when it was handed to delivery
  // and when it arrived; the other steps show no time rather than an invented one. A cancelled order
  // keeps the steps it had reached and ends in a "cancelled" step with the reason.
  const timelineFor=order=>['PENDING','CONFIRMED','PROCESSING',isPickup(order)?'READY_FOR_PICKUP':'SHIPPED','DELIVERED'];
  const stamp=iso=>iso?new Date(iso).toLocaleString('vi-VN',{day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit'}):'';
  const renderTimeline=order=>{
    const cancelled=order.status==='CANCELLED';
    const timelineSteps=timelineFor(order);
    const reached=timelineSteps.indexOf(cancelled?(order.cancellationRequestedFromStatus||'PENDING'):order.status);
    const shipment=order.shipment||{};
    const times={PENDING:order.createdAt,SHIPPED:shipment.shippedAt,DELIVERED:shipment.deliveredAt};
    const items=timelineSteps.map((step,index)=>{
      const state=index<reached||(index===reached&&(step==='DELIVERED'||cancelled))?'done':index===reached?'current':'todo';
      // the current step has no time of its own unless it is one of the three above: show when the order last changed
      const time=times[step]||(state==='current'?order.updatedAt:'');
      return `<li class="${state}"${state==='current'?' aria-current="step"':''}><span class="dot"></span><div><b>${escape(stepText(step,order))}</b>${time?`<small>${escape(stamp(time))}</small>`:''}</div></li>`;
    });
    if(cancelled){
      items.push(`<li class="cancelled" aria-current="step"><span class="dot"></span><div><b>${escape(orderStatusText.CANCELLED)}</b>${order.cancellationRequestedAt?`<small>${escape(stamp(order.cancellationRequestedAt))}</small>`:''}${order.cancellationReason?`<small>Lý do: ${escape(order.cancellationReason)}</small>`:''}</div></li>`);
    }
    detailPanel('orderTimeline',`<h2>Tiến trình đơn hàng</h2><ol class="timeline">${items.join('')}</ol>`);
  };
  const renderDelivery=shipment=>{
    if(!shipment){removePanel('shipmentDetails');return}
    const status=shipmentStatusText[shipment.shipmentStatus]||shipment.shipmentStatus||'—';
    detailPanel('shipmentDetails',`<h2>Thông tin nhận hàng</h2><p><strong>Hình thức:</strong> ${shipment.pickupShowroom?'Nhận tại cửa hàng':'Giao hàng tại nhà'}</p><p><strong>Người nhận:</strong> ${escape(shipment.recipientName)} · ${escape(shipment.phoneNumber)}</p><p><strong>Địa chỉ:</strong> ${escape(shipment.shippingAddress)}</p><p><strong>Giao nhận:</strong> ${escape(status)}${shipment.trackingNumber?` · Mã vận đơn ${escape(shipment.trackingNumber)}`:''}</p>${shipment.shippedAt?`<p><strong>Bàn giao vận chuyển:</strong> ${escape(when(shipment.shippedAt))}</p>`:''}${shipment.deliveredAt?`<p><strong>Đã giao lúc:</strong> ${escape(when(shipment.deliveredAt))}</p>`:''}`);
  };
  // Manager controls: only the valid next order statuses, and the shipment form (UC-16).
  const renderManagerControls=order=>{
    if(!page.startsWith('/manager/orders/')){return}
    const steps=orderSteps(order);
    const shipment=order.shipment;
    const canShip=shipment&&order.status!=='CANCELLED';
    if(!steps.length&&!canShip){removePanel('orderManage');return}
    const stepForm=steps.length?`<form id="orderStatusForm" class="flow-form"><label>Chuyển đơn sang<select name="status">${steps.map(s=>`<option value="${s}">${escape(stepText(s,order))}</option>`).join('')}</select></label><button class="button compact">Cập nhật trạng thái</button></form>`:'';
    const shipForm=canShip?`<form id="shipmentForm" class="flow-form"><label>Trạng thái giao nhận<select name="shipmentStatus">${Object.keys(shipmentStatusText).filter(s=>!isPickup(order)||!['SHIPPED','IN_TRANSIT'].includes(s)).map(s=>`<option value="${s}"${s===shipment.shipmentStatus?' selected':''}>${escape(shipmentStatusText[s])}</option>`).join('')}</select></label><label>Mã vận đơn<input name="trackingNumber" value="${escape(shipment.trackingNumber||'')}" maxlength="100"></label><button class="button compact">Lưu giao nhận</button></form>`:'';
    const panel=detailPanel('orderManage',`<h2>Xử lý đơn hàng</h2><div class="profile-grid">${stepForm}${shipForm}</div>`);
    panel.querySelector('#orderStatusForm')?.addEventListener('submit',async e=>{
      e.preventDefault();
      try{if(await changeOrderStatus(order.id,new FormData(e.target).get('status'))){tell('Trạng thái đơn hàng đã cập nhật.');await load()}}catch(err){tell(err.message,true)}
    });
    panel.querySelector('#shipmentForm')?.addEventListener('submit',async e=>{
      e.preventDefault();
      const form=new FormData(e.target);
      try{await send(`/api/v1/manager/orders/${order.id}/shipment`,'PATCH',{shipmentStatus:form.get('shipmentStatus'),trackingNumber:form.get('trackingNumber')});tell('Đã cập nhật thông tin giao nhận.');await load()}catch(err){tell(err.message,true)}
    });
  };
  // A waiting pickup order shows the day the store keeps the bike until; staff can move it when the customer reschedules.
  const toLocalInput=iso=>{const d=new Date(iso);d.setMinutes(d.getMinutes()-d.getTimezoneOffset());return d.toISOString().slice(0,16)};
  const renderPickupDeadline=order=>{
    if(order?.status!=='READY_FOR_PICKUP'||!order.pickupDeadlineAt){removePanel('pickupDeadline');return}
    const staff=page.startsWith('/manager/orders/');
    const form=staff?`<form id="pickupDeadlineForm" class="flow-form"><label>Đổi hạn giữ xe (khi khách hẹn lại)<input type="datetime-local" name="pickupDeadlineAt" value="${escape(toLocalInput(order.pickupDeadlineAt))}" required></label><button class="button compact">Lưu hạn giữ xe</button></form>`:'';
    const panel=detailPanel('pickupDeadline',`<h2>Hạn giữ xe</h2><p>Xe đã sẵn sàng. Cửa hàng giữ xe cho đến <strong>${escape(when(order.pickupDeadlineAt))}</strong>.${staff?' Chỉ hủy được đơn sau thời điểm này.':' Cần đổi lịch lấy xe, vui lòng liên hệ cửa hàng.'}</p>${form}`);
    panel.querySelector('#pickupDeadlineForm')?.addEventListener('submit',async e=>{
      e.preventDefault();
      const value=new FormData(e.target).get('pickupDeadlineAt');
      try{await send(`/api/v1/manager/orders/${order.id}/pickup-deadline`,'PATCH',{pickupDeadlineAt:value.length===16?value+':00':value});tell('Đã cập nhật hạn giữ xe.');await load()}catch(err){tell(err.message,true)}
    });
  };
  const renderCancellation=order=>{
    let panel=document.querySelector('#cancellationDetails');
    if(order?.status!=='CANCELLED'||!order.cancellationReason){panel?.remove();return;}
    if(!panel){panel=document.createElement('section');panel.id='cancellationDetails';panel.className='data-panel';panel.style.marginTop='20px';document.querySelector('.work-main').appendChild(panel);}
    panel.innerHTML=`<h2>Thông tin hủy đơn</h2><p><strong>Lý do:</strong> ${escape(order.cancellationReason)}</p>${order.cancellationRequestedAt?`<p><strong>Thời điểm hủy:</strong> ${escape(new Date(order.cancellationRequestedAt).toLocaleString('vi-VN'))}</p>`:''}`;
  };
  // ---- list filters (V5) ---------------------------------------------------------------
  // The manager's payment and customer lists filter on the server; the bar below only builds the query string.
  const paymentStatusOptions=[['','Mọi trạng thái'],['PENDING','Chờ thanh toán'],['PAID','Đã thanh toán'],['FAILED','Thất bại'],['CANCELLED','Đã hủy'],['REFUNDED','Đã hoàn tiền']];
  const paymentMethodOptions=[['','Mọi phương thức'],['PAY_LATER','Thanh toán khi nhận xe'],['VNPAY','VNPay']];
  // "Hết hàng" is none left, "Sắp hết" is 3 or fewer. A product with colours can still have one colour sold out,
  // so the exact colour is shown in the stock editor.
  const LOW_STOCK=3;
  const stockStatus=n=>n<=0?'OUT':n<=LOW_STOCK?'LOW':'OK';
  const stockStatusText={OUT:'Hết hàng',LOW:'Sắp hết',OK:'Còn hàng'};
  const stockFilter=()=>[{name:'stockStatus',client:true,options:[['','Mọi tình trạng'],['OUT','Hết hàng'],['LOW','Sắp hết (còn 3 xe trở xuống)'],['OK','Còn hàng']],label:'Tình trạng kho'}];
  const filterBars={
    '/manager/payments':[{name:'paymentStatus',options:paymentStatusOptions,label:'Trạng thái thanh toán'},{name:'paymentMethod',options:paymentMethodOptions,label:'Phương thức thanh toán'},{name:'search',placeholder:'Tìm mã đơn, email, người nhận hoặc mã giao dịch',label:'Tìm kiếm'}],
    '/customer/payments':[{name:'paymentStatus',client:true,options:paymentStatusOptions,label:'Trạng thái thanh toán'}],
    '/manager/inventory':stockFilter(),'/manager/products':stockFilter(),'/admin/inventory':stockFilter(),'/admin/products':stockFilter(),
    '/manager/customers':[{name:'search',placeholder:'Tìm theo tên, email hoặc tài khoản',label:'Tìm kiếm'}],
    '/manager/reports':[{name:'period',options:[['day','Theo ngày'],['month','Theo tháng'],['quarter','Theo quý'],['year','Theo năm']],label:'Gộp theo'},{name:'from',type:'date',label:'Từ ngày'},{name:'to',type:'date',label:'Đến ngày'}]
  };
  const filterQuery=new URLSearchParams();
  const clientQuery={};
  // Rows get a readable stock status; client-side filters then keep only the rows that match.
  const clientFiltered=rows=>{
    if(!Array.isArray(rows))return rows;
    let out=rows.map(r=>r&&typeof r==='object'&&'stockQuantity' in r?{...r,stockStatus:stockStatusText[stockStatus(Number(r.stockQuantity)||0)],_stock:stockStatus(Number(r.stockQuantity)||0)}:r);
    for(const [name,value] of Object.entries(clientQuery)){
      out=out.filter(r=>name==='stockStatus'?r._stock===value:String(r[name])===value);
    }
    return out;
  };
  const withFilters=url=>filterQuery.toString()?url+(url.includes('?')?'&':'?')+filterQuery:url;
  const setupFilters=()=>{
    const bar=document.querySelector('#filters'),fields=filterBars[page];
    if(!bar||!fields)return;
    bar.innerHTML=fields.map(f=>f.options
      ?`<label>${escape(f.label)}<select name="${f.name}">${f.options.map(([v,t])=>`<option value="${v}">${escape(t)}</option>`).join('')}</select></label>`
      :`<label>${escape(f.label)}<input type="${f.type||'search'}" name="${f.name}" placeholder="${escape(f.placeholder||'')}" autocomplete="off"></label>`).join('')+'<button type="button" class="button compact ghost" id="clearFilters">Xóa bộ lọc</button>';
    bar.hidden=false;
    let timer;
    const apply=()=>{
      for(const f of fields){const v=bar.querySelector(`[name="${f.name}"]`).value.trim();if(f.client){if(v)clientQuery[f.name]=v;else delete clientQuery[f.name]}else if(v)filterQuery.set(f.name,v);else filterQuery.delete(f.name)}
      load();
    };
    bar.addEventListener('change',apply);
    bar.addEventListener('input',e=>{if(e.target.type==='search'){clearTimeout(timer);timer=setTimeout(apply,300)}});
    bar.querySelector('#clearFilters').addEventListener('click',()=>{bar.querySelectorAll('input').forEach(el=>el.value='');bar.querySelectorAll('select').forEach(el=>el.selectedIndex=0);apply()});
  };
  const load=async()=>{if(!api){loading.hidden=true;empty.hidden=true;return}loading.hidden=false;empty.hidden=true;try{const data=await get(withFilters(api));if(secondaryApi)renderPromotions(await get(secondaryApi));if(mode==='dashboard'){renderMetrics(data);if(page==='/manager/reports'){document.querySelector('#panel-title').textContent='Doanh thu theo kỳ';renderTable(data?.breakdown||[]);renderRevenueChart(data?.breakdown||[]);let extra=document.querySelector('#report-products');if(!extra){extra=document.createElement('section');extra.id='report-products';extra.className='data-panel report-products';document.querySelector('.work-main').appendChild(extra)}extra.innerHTML=`<div class="report-columns"><div><p class="eyebrow">KINETIC / SẢN PHẨM</p><h2>Sản phẩm bán chạy</h2>${reportList(data?.topProducts||[])}</div><div><p class="eyebrow">KINETIC / TỒN KHO</p><h2>Sản phẩm bán chậm</h2>${reportList(data?.slowProducts||[])}</div></div>`;return}const rows=page==='/admin'?(data?.auditLogs||[]):page==='/manager'?(data?.recentOrders||[]):data;document.querySelector('#panel-title').textContent=page==='/admin'?'Nhật ký hoạt động gần đây':'Đơn hàng gần đây';renderTable(Array.isArray(rows)?rows:[])}else if(mode==='profile'){loading.hidden=true;renderProfile(data||{});renderTable([])}else if(mode==='detail'){document.querySelector('#panel-title').textContent='Thông tin đơn hàng';renderTable([data]);renderTimeline(data);renderOrderItems(data);renderPricing(data);renderDelivery(data.shipment);renderManagerControls(data);renderPickupDeadline(data);renderCancellation(data)}else if(page==='/favorites'){document.querySelector('#panel-title').textContent='Xe bạn đã lưu';renderFavorites(data)}else{document.querySelector('#panel-title').textContent=mode==='inventory'?'Danh mục xe và tồn kho':mode==='pricing'?'Quy tắc giá hiện hành':'Danh sách';renderTable(clientFiltered(data))}}catch(err){loading.hidden=true;tell(err.message,true)}};
  document.querySelector('#refresh')?.addEventListener('click',load);document.querySelector('#logout')?.addEventListener('click',async()=>{try{await send('/api/v1/auth/logout','POST')}finally{location.href='/'}});
  table?.addEventListener('click',async e=>{const b=e.target.closest('[data-action]');if(!b)return;const id=b.dataset.id;try{if(b.dataset.action==='pay-vnpay'){b.disabled=true;try{const payment=await send('/api/v1/payments/vnpay/create','POST',{orderId:Number(id),language:'vn'});location.href=payment.paymentUrl}finally{b.disabled=false}return}if(b.dataset.action==='confirm-payment')await send(`/api/v1/manager/payments/${id}/confirm`,'PATCH',{});else if(b.dataset.action==='refund-payment'){const note=prompt('Ghi chú hoàn tiền (mã giao dịch hoàn, ngày chuyển...)');if(note===null)return;await send(`/api/v1/manager/payments/${id}/refund`,'PATCH',{note})}else if(b.dataset.action==='stock'){if(b.dataset.slug&&window.KineticStock){const detail=await get(`/api/v1/products/${encodeURIComponent(b.dataset.slug)}`);if((detail.variants||[]).length){await window.KineticStock.open(detail,send,tell);await load();return}}const stockQuantity=Number(prompt('Nhập số lượng tồn kho mới',b.dataset.stock));if(!Number.isInteger(stockQuantity)||stockQuantity<0)return;await send(`/api/v1/manager/products/${id}/stock`,'PATCH',{stockQuantity})}else if(b.dataset.action==='account-status'){const status=b.dataset.status==='ACTIVE'?'INACTIVE':'ACTIVE';await send(`/api/v1/admin/accounts/${id}/status`,'PATCH',{status})}else if(b.dataset.action==='pricing'){const amount=Number(prompt('Số tiền áp dụng',b.dataset.amount));if(!Number.isFinite(amount)||amount<0)return;await send(`/api/v1/admin/pricing-rules/${id}`,'PATCH',{amount,status:b.dataset.status})}else if(b.dataset.action==='support-status'){const note=prompt('Ghi chú xử lý (có thể để trống)');if(note===null)return;await send(`/api/v1/manager/support-requests/${id}`,'PATCH',{status:b.dataset.status,staffNote:note})}else if(b.dataset.action==='read')await send(`/api/v1/notifications/${id}/read`,'PATCH',{});else if(b.dataset.action==='cancel-order'){const reason=prompt('Lý do hủy đơn hàng');if(reason===null)return;if(!reason.trim()){tell('Vui lòng nhập lý do hủy đơn hàng.',true);return}await send(`/api/v1/orders/${id}/cancellation-request`,'POST',{reason:reason.trim()});tell('Đã hủy đơn hàng.');await load();return}else if(b.dataset.action==='remove-favorite')await send(`/api/v1/favorites/${id}`,'DELETE');else return;tell('Đã cập nhật dữ liệu.');await load()}catch(err){tell(err.message,true)}});
  table?.addEventListener('change',async e=>{const s=e.target.closest('[data-action="order-status"]');if(!s||!s.value)return;try{if(await changeOrderStatus(s.dataset.id,s.value))tell('Trạng thái đơn hàng đã cập nhật.');await load()}catch(err){tell(err.message,true)}});
  // Guest orders are attached only after the customer proves they own the account email with an OTP.
  const linkButton=document.querySelector('#link-guest-orders');
  if(page==='/customer/orders'&&linkButton){
    linkButton.hidden=false;
    linkButton.addEventListener('click',async()=>{
      linkButton.disabled=true;
      try{
        const profile=await get('/api/v1/auth/profile');
        const email=profile?.email;
        if(!email){tell('Tài khoản chưa có email để xác minh.',true);return}
        if(!confirm(`Hệ thống sẽ gửi mã xác minh tới ${email} để tìm các đơn bạn đã đặt bằng email này khi chưa đăng nhập. Tiếp tục?`))return;
        let sent;
        try{sent=await send('/api/v1/orders/email-verification/send','POST',{email})}
        catch(err){tell('Chưa gửi được mã xác minh qua email. Vui lòng thử lại sau hoặc liên hệ cửa hàng.',true);return}
        const code=prompt(`Nhập mã gồm 6 chữ số đã gửi tới ${email}`);
        if(code===null)return;
        if(!/^\d{6}$/.test(code.trim())){tell('Mã xác minh gồm 6 chữ số.',true);return}
        await send('/api/v1/orders/email-verification/verify','POST',{verificationSessionId:sent.verificationSessionId,code:code.trim()});
        const result=await send('/api/v1/orders/guest-orders/link','POST',{emailVerificationSessionId:sent.verificationSessionId});
        tell(result.linkedOrders?`Đã liên kết ${result.linkedOrders} đơn hàng vào tài khoản.`:'Không tìm thấy đơn nào đặt bằng email này khi chưa đăng nhập.');
        await load();
      }catch(err){tell(err.message,true)}
      finally{linkButton.disabled=false}
    });
  }
  if(page==='/admin/accounts'){const b=document.querySelector('#create-account');if(b)b.hidden=false}window.addEventListener('workspace:reload',()=>load());setupFilters();load();
})();
