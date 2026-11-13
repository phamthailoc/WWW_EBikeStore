(() => {
  const body = document.body, page = body.dataset.page || '', api = body.dataset.api || '', secondaryApi = body.dataset.secondaryApi || '', mode = body.dataset.mode || 'table';
  const notice = document.querySelector('#notice'), loading = document.querySelector('#loading'), empty = document.querySelector('#empty'), table = document.querySelector('#data-table'), metrics = document.querySelector('#metrics'), profile = document.querySelector('#profile');
  let promotions = [], records = [];
  const names = {customerName:t('Khách hàng'),subject:t('Tiêu đề'),message:t('Nội dung'),staffNote:t('Ghi chú xử lý'),stockStatus:t('Tình trạng kho'),paymentStatus:t('Trạng thái thanh toán'),role:t('Mã vai trò'),title:t('Vai trò'),permissions:t('Quyền'),actor:t('Người thực hiện'),action:t('Hành động'),target:t('Đối tượng'),createdAt:t('Thời gian'),id:t('Mã'),name:t('Tên'),slug:t('Đường dẫn'),description:t('Mô tả'),price:t('Giá'),discountPrice:t('Giá ưu đãi'),rating:t('Đánh giá'),reviewCount:t('Lượt đánh giá'),stockQuantity:t('Tồn kho'),featured:t('Nổi bật'),category:t('Danh mục'),images:t('Hình ảnh'),orderId:t('Mã đơn hàng'),orderNumber:t('Mã đơn hàng'),status:t('Trạng thái'),createdAt:t('Ngày tạo'),updatedAt:t('Cập nhật'),totalAmount:t('Tổng tiền'),paymentMethod:t('Phương thức thanh toán'),paymentStatus:t('Trạng thái thanh toán'),customerName:t('Khách hàng'),customerEmail:t('Email khách hàng'),email:'Email',phone:t('Điện thoại'),phoneNumber:t('Điện thoại'),role:t('Vai trò'),roles:t('Vai trò'),username:t('Tài khoản'),accountType:t('Loại tài khoản'),amount:t('Số tiền'),code:t('Mã ưu đãi'),campaignName:t('Chiến dịch'),discountType:t('Loại ưu đãi'),discountValue:t('Giá trị ưu đãi'),startsAt:t('Bắt đầu'),endsAt:t('Kết thúc'),totalOrders:t('Tổng đơn hàng'),successfulOrders:t('Đơn hoàn tất'),pendingOrders:t('Đơn đang chờ'),unpaidPayLaterOrders:t('Đơn trả sau chưa thu'),todayRevenue:t('Doanh thu hôm nay'),weekRevenue:t('Doanh thu tuần'),monthRevenue:t('Doanh thu tháng'),totalRevenue:t('Tổng doanh thu'),totalAccounts:t('Tổng tài khoản'),adminAccounts:t('Tài khoản quản trị'),customerAccounts:t('Tài khoản khách hàng'),lockedAccounts:t('Tài khoản bị khóa'),verified:t('Đã xác minh'),readAt:t('Ngày đã đọc'),action:t('Thao tác'),actor:t('Người thực hiện'),target:t('Mục tiêu'),ipAddress:t('Địa chỉ IP'),type:t('Loại'),message:t('Nội dung'),title:t('Tiêu đề'),quantitySold:t('Đã bán'),productName:t('Sản phẩm'),unit:t('Đơn vị'),effectiveFrom:t('Có hiệu lực từ'),transactionReference:t('Mã giao dịch'),paidAt:t('Ngày thanh toán'),subtotal:t('Tạm tính'),shippingFee:t('Phí giao xe'),discountAmount:t('Giảm giá'),registrationFee:t('Phí đăng ký'),includeRegistrationService:t('Dịch vụ hồ sơ'),notes:t('Ghi chú'),shipment:t('Giao nhận'),label:t('Kỳ báo cáo'),orderCount:t('Số đơn hàng'),revenue:'Doanh thu'};
  const label = key => names[key] || key.replace(/([A-Z])/g, ' $1').replace(/^./, c => c.toUpperCase());
  const escape = value => String(value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const orderStatusText={PENDING:t('Chờ xử lý'),CONFIRMED:t('Đã xác nhận'),PROCESSING:t('Đang xử lý'),READY_FOR_PICKUP:t('Chờ lấy'),SHIPPED:t('Đang giao'),DELIVERED:t('Đã giao'),CANCELLED:t('Đã hủy')};
  const valueText = {
    orderStatus:orderStatusText,
    paymentStatus:{PENDING:t('Chờ thanh toán'),AUTHORIZED:t('Đã ủy quyền'),PAID:t('Đã thanh toán'),FAILED:t('Thất bại'),CANCELLED:t('Đã hủy'),REFUNDED:t('Đã hoàn tiền')},
    paymentMethod:{PAY_LATER:t('Thanh toán khi nhận xe'),VNPAY:'VNPay'},
    status:{...orderStatusText,NEW:t('Mới'),IN_PROGRESS:t('Đang xử lý'),RESOLVED:t('Đã giải quyết')}
  };
  const human = (value,key='') => {
    if (value == null) return '—';
    if (valueText[key] && valueText[key][value]) return valueText[key][value];
    if (typeof value === 'object') return Array.isArray(value) ? value.map(item=>typeof item==='object'?(item.name||item.colorName||''):String(item)).join(', ') : (value.name || value.colorName || value.label || '');
    if (typeof value === 'boolean') return value ? t('Có') : t('Không');
    if (typeof value === 'number' && /amount|price|revenue|spent|fee/i.test(key)) return new Intl.NumberFormat('vi-VN').format(value) + ' ₫';
    return String(value);
  };
  const tell = (message,error=false) => { notice.textContent=message; notice.hidden=false; notice.classList.toggle('error',error); };
  // Server errors come back as JSON ({status, error, message}); show only the message, not the raw body.
  const errorText = async(r,fallback) => { const body=await r.text(); try{const j=JSON.parse(body); if(j&&typeof j.message==='string'&&j.message.trim())return j.message.trim()}catch(ignored){} return body.slice(0,240)||fallback; };
  const get = async url => { const r=await fetch(url,{credentials:'same-origin',headers:{Accept:'application/json'}}); if(r.status===401){location.href='/login?returnTo='+encodeURIComponent(location.pathname);throw Error(t('Vui lòng đăng nhập để tiếp tục.'))} if(!r.ok)throw Error(await errorText(r,t('Không tải được dữ liệu.'))); return r.status===204?null:r.json(); };
  const send = async(url,method,data) => { const r=await fetch(url,{method,credentials:'same-origin',headers:{'Content-Type':'application/json',Accept:'application/json'},body:data===undefined?undefined:JSON.stringify(data)});if(r.status===401){location.href='/login';throw Error(t('Phiên đăng nhập đã hết hạn.'))}if(!r.ok)throw Error(await errorText(r,t('Không thể lưu thay đổi.')));return r.status===204?null:r.json(); };
  const vnpayEnabled = body.dataset.vnpayEnabled === 'true';
  // VNPay orders have no payment row until the first payment URL is created, so a missing method also means VNPay.
  const awaitsVnPay = row => vnpayEnabled && row.orderNumber != null && row.status !== 'CANCELLED'
    && (row.paymentMethod == null || row.paymentMethod === 'VNPAY') && !['PAID','REFUNDED'].includes(row.paymentStatus);
  const actionFor = row => {
    const id=row.id??row.orderId??row.paymentId??row.productId, buttons=[];
    if(page==='/manager/payments'&&id!=null&&row.paymentMethod==='PAY_LATER'&&row.paymentStatus==='PENDING'&&row.orderStatus!=='CANCELLED')buttons.push(`<button class="table-action" data-action="confirm-payment" data-id="${id}">${t('Xác nhận đã thu')}</button>`);
    if(page==='/manager/payments'&&id!=null&&row.paymentStatus==='PAID'&&row.orderStatus==='CANCELLED')buttons.push(`<button class="table-action" data-action="refund-payment" data-id="${id}">${t('Ghi nhận hoàn tiền')}</button>`);
    if(page==='/manager/orders'&&id!=null&&orderSteps(row).length)buttons.push(`<select class="table-action" data-action="order-status" data-id="${id}"><option value="">${t('Cập nhật trạng thái…')}</option>${orderSteps(row).map(s=>`<option value="${s}">${stepText(s,row)}</option>`).join('')}</select>`);
    if(page.startsWith('/manager')&&id!=null&&(page.includes('inventory')||page.includes('products')))buttons.push(`<button class="table-action" data-action="stock" data-id="${id}" data-slug="${escape(row.slug||'')}" data-stock="${row.stockQuantity??0}">${t('Cập nhật tồn kho')}</button><button class="table-action" data-action="images" data-id="${id}" data-slug="${escape(row.slug||'')}" data-name="${escape(row.name||'')}">${t('Quản lý ảnh')}</button>`);
    if(page.startsWith('/admin')&&id!=null&&(page.includes('inventory')||page.includes('products')))buttons.push(`<button class="table-action" data-action="stock" data-id="${id}" data-slug="${escape(row.slug||'')}" data-stock="${row.stockQuantity??0}">${t('Cập nhật tồn kho')}</button><button class="table-action" data-action="images" data-id="${id}" data-slug="${escape(row.slug||'')}" data-name="${escape(row.name||'')}">${t('Quản lý ảnh')}</button>`);
    if(page==='/admin/accounts'&&id!=null)buttons.push(`<button class="table-action" data-action="role" data-id="${id}">${t('Đổi vai trò')}</button><button class="table-action" data-action="account-status" data-id="${id}" data-status="${row.status||'ACTIVE'}">${row.status==='ACTIVE'?t('Vô hiệu hóa'):t('Kích hoạt lại')}</button><button class="table-action" data-action="delete-account" data-id="${id}" data-email="${escape(row.email||'')}">${t('Xóa')}</button>`);
    if(page==='/admin/pricing'&&id!=null)buttons.push(`<button class="table-action" data-action="pricing" data-id="${id}" data-amount="${row.amount||0}" data-status="${row.status||''}">${t('Sửa quy tắc')}</button>`);
    if(page==='/manager/support'&&id!=null){if(row.status==='NEW')buttons.push(`<button class="table-action" data-action="support-status" data-id="${id}" data-status="IN_PROGRESS">${t('Nhận xử lý')}</button>`);if(row.status!=='RESOLVED')buttons.push(`<button class="table-action" data-action="support-status" data-id="${id}" data-status="RESOLVED">${t('Đã giải quyết')}</button>`);else buttons.push(`<button class="table-action" data-action="support-status" data-id="${id}" data-status="IN_PROGRESS">${t('Mở lại')}</button>`)}
    if(page==='/customer/notifications'&&id!=null&&!row.readAt)buttons.push(`<button class="table-action" data-action="read" data-id="${id}">${t('Đánh dấu đã đọc')}</button>`);
    if(page.startsWith('/customer/orders')&&id!=null&&awaitsVnPay(row))buttons.push(`<button class="table-action" data-action="pay-vnpay" data-id="${id}">${t('Thanh toán VNPay')}</button>`);
    if(page.startsWith('/customer/orders')&&id!=null&&['PENDING','CONFIRMED','PROCESSING'].includes(row.status))buttons.push(`<button class="table-action" data-action="cancel-order" data-id="${id}">${t('Hủy đơn')}</button>`);
    if(page==='/favorites'&&id!=null)buttons.push(`<a class="table-action" href="/product/${encodeURIComponent(row.slug||'')}">${t('Xem xe')}</a><button class="table-action" data-action="remove-favorite" data-id="${id}">${t('Bỏ yêu thích')}</button>`);
    if((page==='/customer/orders'||page==='/manager/orders'||page==='/manager')&&id!=null)buttons.push(`<a class="table-action" href="${page.startsWith('/manager')?'/manager':'/customer'}/orders/${id}">${t('Chi tiết')}</a>`);
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
    table.querySelector('thead').innerHTML=`<tr>${keys.map(k=>`<th data-field="${escape(k)}">${escape(label(k))}</th>`).join('')}${hasActions?'<th>' + t('Thao tác') + '</th>':''}</tr>`;
    table.querySelector('tbody').innerHTML=records.map(row=>`<tr>${keys.map(k=>`<td data-field="${escape(k)}">${escape(human(row[k],k))}</td>`).join('')}${hasActions?`<td>${actionFor(row)}</td>`:''}</tr>`).join('');
  };
  const renderFavorites = rows => {
    const list=Array.isArray(rows)?rows:[]; loading.hidden=true; empty.hidden=!!list.length; table.hidden=true;
    if(!list.length)return;
    const panel=document.querySelector('#data-panel'); panel.classList.add('favorite-panel');
    panel.innerHTML=`<div class="favorite-grid">${list.map(p=>{const image=p.images?.[0]||'/images/hero-fallback.svg';return `<article class="favorite-card"><a href="/product/${encodeURIComponent(p.slug)}"><img src="${escape(image)}" alt="${escape(p.name)}"><p class="eyebrow">${escape(p.category?.name||'KINETIC SELECT')}</p><h2>${escape(p.name)}</h2><p class="favorite-price">${escape(new Intl.NumberFormat('vi-VN').format(p.discountPrice||p.price))} ₫</p></a><div class="product-action"><a class="button compact ghost" href="/product/${encodeURIComponent(p.slug)}">${t('Xem chi tiết')}</a><button class="table-action" data-action="remove-favorite" data-id="${p.id}">${t('Bỏ yêu thích')}</button></div></article>`}).join('')}</div>`;
  };
  const renderPromotions=rows=>{promotions=Array.isArray(rows)?rows:[];const panel=document.querySelector('#promo-panel');if(!panel)return;panel.hidden=false;const tbl=document.querySelector('#promo-table');if(!promotions.length){tbl.querySelector('tbody').innerHTML='<tr><td>' + t('Chưa có chương trình.') + '</td></tr>';return}const keys=Object.keys(promotions[0]);tbl.querySelector('thead').innerHTML=`<tr>${keys.map(k=>`<th>${escape(label(k))}</th>`).join('')}<th>${t('Thao tác')}</th></tr>`;tbl.querySelector('tbody').innerHTML=promotions.map(p=>`<tr>${keys.map(k=>`<td>${escape(human(p[k],k))}</td>`).join('')}<td><button class="table-action" data-action="promotion" data-id="${p.id}">${t('Sửa ưu đãi')}</button></td></tr>`).join('')};
  const renderMetrics=data=>{if(!data||typeof data!=='object')return;const entries=Object.entries(data).filter(([k,v])=>typeof v==='number'&&!/id$/i.test(k));metrics.innerHTML=entries.map(([k,v])=>`<article class="metric"><small>${escape(label(k))}</small><b>${escape(human(v,k))}</b></article>`).join('')};
  // Bar chart of revenue per period, drawn as inline SVG (no library); the table below is its text equivalent.
  const renderRevenueChart=points=>{
    let panel=document.querySelector('#report-chart');
    if(!panel){panel=document.createElement('section');panel.id='report-chart';panel.className='data-panel report-chart';document.querySelector('#data-panel').before(panel)}
    if(!points.length||points.every(p=>!Number(p.revenue))){panel.innerHTML='<p class="eyebrow">KINETIC / ' + t('DOANH THU') + '</p><h2>' + t('Biểu đồ doanh thu') + '</h2><p>' + t('Chưa có doanh thu trong khoảng thời gian này.') + '</p>';return}
    const W=720,H=260,left=64,bottom=44,top=16,right=12,plotW=W-left-right,plotH=H-top-bottom;
    const max=Math.max(...points.map(p=>Number(p.revenue)||0));
    const step=Math.pow(10,Math.floor(Math.log10(max)));const niceMax=Math.ceil(max/step)*step;
    const slot=plotW/points.length,barW=Math.min(48,slot*0.64);
    const short=v=>v>=1e9?(v/1e9).toFixed(1).replace('.0','')+' ' + t('tỷ') + '':v>=1e6?(v/1e6).toFixed(1).replace('.0','')+' '+t('tr'):new Intl.NumberFormat('vi-VN').format(v);
    const grid=[0,0.5,1].map(t=>{const y=top+plotH*(1-t);return `<line x1="${left}" x2="${W-right}" y1="${y}" y2="${y}" class="grid"/><text x="${left-8}" y="${y+4}" text-anchor="end">${short(niceMax*t)}</text>`}).join('');
    const bars=points.map((p,i)=>{const v=Number(p.revenue)||0,h=plotH*v/niceMax,x=left+slot*i+(slot-barW)/2,y=top+plotH-h;
      const every=Math.ceil(points.length/12);
      return `<g><rect x="${x}" y="${y}" width="${barW}" height="${Math.max(h,0)}" rx="3"><title>${escape(p.label)}: ${escape(human(v,'revenue'))} (${p.orderCount} ${t('đơn)')}</title></rect>${i%every===0?`<text x="${x+barW/2}" y="${H-bottom+18}" text-anchor="middle">${escape(String(p.label).slice(0,10))}</text>`:''}</g>`}).join('');
    const total=points.reduce((sum,p)=>sum+(Number(p.revenue)||0),0);
    panel.innerHTML=`<p class="eyebrow">KINETIC / ${t('DOANH THU')}</p><h2>${t('Biểu đồ doanh thu')}</h2><svg viewBox="0 0 ${W} ${H}" class="revenue-chart" role="img" aria-label="${t('Biểu đồ cột doanh thu theo kỳ, tổng')} ${escape(human(total,'revenue'))}">${grid}${bars}</svg>`;
  };
  const reportList=rows=>rows.length?`<ol class="report-list">${rows.map(r=>`<li><span><b>${escape(r.productName||t('Sản phẩm'))}</b><small>${escape(human(r.quantitySold,'quantitySold'))} ${t('xe đã bán')}</small></span><strong>${escape(human(r.revenue,'revenue'))}</strong></li>`).join('')}</ol>`:'<p class="empty">' + t('Chưa có dữ liệu trong kỳ này.') + '</p>';
  const renderProfile=data=>{profile.hidden=false;const fields=['firstName','lastName','email','phone','phoneNumber'];profile.innerHTML=`<form id="profileForm"><p class="eyebrow">${t('THÔNG TIN TÀI KHOẢN')}</p><div class="profile-grid">${fields.filter(f=>f in(data||{})).map(f=>`<label>${escape(label(f))}<input name="${f}" value="${escape(data[f]||'')}"></label>`).join('')}</div><p><button class="button compact">${t('Lưu hồ sơ')}</button></p></form>`;profile.querySelector('form').addEventListener('submit',async e=>{e.preventDefault();try{await send('/api/v1/auth/profile','PUT',Object.fromEntries(new FormData(e.currentTarget)));tell(t('Hồ sơ đã được cập nhật.'))}catch(err){tell(err.message,true)}})};
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
  const stepText=(status,order)=>status==='DELIVERED'&&isPickup(order)?t('Đã nhận'):orderStatusText[status];
  const shipmentStatusText={PENDING:t('Chưa chuẩn bị'),PREPARING:t('Đang chuẩn bị'),SHIPPED:t('Đã bàn giao vận chuyển'),IN_TRANSIT:t('Đang vận chuyển'),DELIVERED:t('Đã giao'),RETURNED:t('Hoàn trả')};
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
      const reason=prompt(t('Lý do hủy đơn hàng'));
      if(reason===null)return false;
      if(!reason.trim()){tell(t('Vui lòng nhập lý do hủy đơn hàng.'),true);return false}
      payload.reason=reason.trim();
    }
    await send(`/api/v1/orders/${id}/status`,'PATCH',payload);
    return true;
  };
  const renderOrderItems=order=>{
    const items=order.items||[];
    if(!items.length){removePanel('orderItems');return}
    const unitsOf=item=>(item.units||[]).map(u=>`<small class="unit-numbers">${t('Số khung')} ${escape(u.frameNumber)} ${t('· Số máy')} ${escape(u.engineNumber)}</small>`).join('');
    const rows=items.map(item=>`<tr><td>${escape(item.productName||'—')}${unitsOf(item)}</td><td>${escape(item.colorName||'—')}</td><td>${escape(item.quantity??0)}</td><td>${escape(money(item.unitPrice))}</td><td>${escape(money(item.lineTotal))}</td></tr>`).join('');
    detailPanel('orderItems',`<h2>${t('Xe trong đơn hàng')}</h2><div class="table-scroll"><table><thead><tr><th>${t('Xe')}</th><th>${t('Màu')}</th><th>${t('Số lượng')}</th><th>${t('Đơn giá')}</th><th>${t('Thành tiền')}</th></tr></thead><tbody>${rows}</tbody></table></div>`);
  };
  const renderPricing=order=>{
    const line=(name,value)=>`<tr><td>${name}</td><td data-price="${escape(name)}">${escape(value)}</td></tr>`;
    const lines=[line(t('Tạm tính'),money(order.subtotal))];
    if(order.includeRegistrationService)lines.push(line(t('Dịch vụ đăng ký xe'),money(order.registrationFee)));
    if(Number(order.shippingFee)>0)lines.push(line(t('Phí giao hàng'),money(order.shippingFee)));
    if(Number(order.discountAmount)>0)lines.push(line(t('Giảm giá'),'−'+money(order.discountAmount)));
    lines.push(line('<strong>' + t('Tổng cộng') + '</strong>',money(order.totalAmount)));
    const payment=`${order.paymentMethod||'—'} · ${order.paymentStatus||t('Chưa có giao dịch')}`;
    detailPanel('orderPricing',`<h2>${t('Giá và thanh toán')}</h2><div class="table-scroll"><table><tbody>${lines.join('')}</tbody></table></div><p><strong>${t('Thanh toán:')}</strong> ${escape(payment)}</p>${order.notes?`<p><strong>${t('Ghi chú:')}</strong> ${escape(order.notes)}</p>`:''}`);
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
      items.push(`<li class="cancelled" aria-current="step"><span class="dot"></span><div><b>${escape(orderStatusText.CANCELLED)}</b>${order.cancellationRequestedAt?`<small>${escape(stamp(order.cancellationRequestedAt))}</small>`:''}${order.cancellationReason?`<small>${t('Lý do:')} ${escape(order.cancellationReason)}</small>`:''}</div></li>`);
    }
    detailPanel('orderTimeline',`<h2>${t('Tiến trình đơn hàng')}</h2><ol class="timeline">${items.join('')}</ol>`);
  };
  const renderDelivery=shipment=>{
    if(!shipment){removePanel('shipmentDetails');return}
    const status=shipmentStatusText[shipment.shipmentStatus]||shipment.shipmentStatus||'—';
    detailPanel('shipmentDetails',`<h2>${t('Thông tin nhận hàng')}</h2><p><strong>${t('Hình thức:')}</strong> ${shipment.pickupShowroom?t('Nhận tại cửa hàng'):t('Giao hàng tại nhà')}</p><p><strong>${t('Người nhận:')}</strong> ${escape(shipment.recipientName)} · ${escape(shipment.phoneNumber)}</p><p><strong>${t('Địa chỉ:')}</strong> ${escape(shipment.shippingAddress)}</p><p><strong>${t('Giao nhận:')}</strong> ${escape(status)}${shipment.trackingNumber?` ${t('· Mã vận đơn')} ${escape(shipment.trackingNumber)}`:''}</p>${shipment.shippedAt?`<p><strong>${t('Bàn giao vận chuyển:')}</strong> ${escape(when(shipment.shippedAt))}</p>`:''}${shipment.deliveredAt?`<p><strong>${t('Đã giao lúc:')}</strong> ${escape(when(shipment.deliveredAt))}</p>`:''}`);
  };
  // Manager controls: only the valid next order statuses, and the shipment form (UC-16).
  const renderManagerControls=order=>{
    if(!page.startsWith('/manager/orders/')){return}
    const steps=orderSteps(order);
    const shipment=order.shipment;
    const canShip=shipment&&order.status!=='CANCELLED';
    if(!steps.length&&!canShip){removePanel('orderManage');return}
    const stepForm=steps.length?`<form id="orderStatusForm" class="flow-form"><label>${t('Chuyển đơn sang')}<select name="status">${steps.map(s=>`<option value="${s}">${escape(stepText(s,order))}</option>`).join('')}</select></label><button class="button compact">${t('Cập nhật trạng thái')}</button></form>`:'';
    const shipForm=canShip?`<form id="shipmentForm" class="flow-form"><label>${t('Trạng thái giao nhận')}<select name="shipmentStatus">${Object.keys(shipmentStatusText).filter(s=>!isPickup(order)||!['SHIPPED','IN_TRANSIT'].includes(s)).map(s=>`<option value="${s}"${s===shipment.shipmentStatus?' selected':''}>${escape(shipmentStatusText[s])}</option>`).join('')}</select></label><label>${t('Mã vận đơn')}<input name="trackingNumber" value="${escape(shipment.trackingNumber||'')}" maxlength="100"></label><button class="button compact">${t('Lưu giao nhận')}</button></form>`:'';
    const panel=detailPanel('orderManage',`<h2>${t('Xử lý đơn hàng')}</h2><div class="profile-grid">${stepForm}${shipForm}</div>`);
    panel.querySelector('#orderStatusForm')?.addEventListener('submit',async e=>{
      e.preventDefault();
      try{if(await changeOrderStatus(order.id,new FormData(e.target).get('status'))){tell(t('Trạng thái đơn hàng đã cập nhật.'));await load()}}catch(err){tell(err.message,true)}
    });
    panel.querySelector('#shipmentForm')?.addEventListener('submit',async e=>{
      e.preventDefault();
      const form=new FormData(e.target);
      try{await send(`/api/v1/manager/orders/${order.id}/shipment`,'PATCH',{shipmentStatus:form.get('shipmentStatus'),trackingNumber:form.get('trackingNumber')});tell(t('Đã cập nhật thông tin giao nhận.'));await load()}catch(err){tell(err.message,true)}
    });
  };
  // A waiting pickup order shows the day the store keeps the bike until; staff can move it when the customer reschedules.
  const toLocalInput=iso=>{const d=new Date(iso);d.setMinutes(d.getMinutes()-d.getTimezoneOffset());return d.toISOString().slice(0,16)};
  const renderPickupDeadline=order=>{
    if(order?.status!=='READY_FOR_PICKUP'||!order.pickupDeadlineAt){removePanel('pickupDeadline');return}
    const staff=page.startsWith('/manager/orders/');
    const form=staff?`<form id="pickupDeadlineForm" class="flow-form"><label>${t('Đổi hạn giữ xe (khi khách hẹn lại)')}<input type="datetime-local" name="pickupDeadlineAt" value="${escape(toLocalInput(order.pickupDeadlineAt))}" required></label><button class="button compact">${t('Lưu hạn giữ xe')}</button></form>`:'';
    const panel=detailPanel('pickupDeadline',`<h2>${t('Hạn giữ xe')}</h2><p>${t('Xe đã sẵn sàng. Cửa hàng giữ xe cho đến')} <strong>${escape(when(order.pickupDeadlineAt))}</strong>.${staff?' ' + t('Chỉ hủy được đơn sau thời điểm này.') + '':' ' + t('Cần đổi lịch lấy xe, vui lòng liên hệ cửa hàng.') + ''}</p>${form}`);
    panel.querySelector('#pickupDeadlineForm')?.addEventListener('submit',async e=>{
      e.preventDefault();
      const value=new FormData(e.target).get('pickupDeadlineAt');
      try{await send(`/api/v1/manager/orders/${order.id}/pickup-deadline`,'PATCH',{pickupDeadlineAt:value.length===16?value+':00':value});tell(t('Đã cập nhật hạn giữ xe.'));await load()}catch(err){tell(err.message,true)}
    });
  };
  // V18: frame and engine number of every vehicle, entered by staff before the bike is handed over.
  const renderUnitsForm=order=>{
    const tracked=(order.items||[]).filter(item=>item.requiresUnitNumbers);
    const open=['PROCESSING','READY_FOR_PICKUP','SHIPPED','DELIVERED'].includes(order.status);
    if(!page.startsWith('/manager/orders/')||!tracked.length||!open){removePanel('orderUnits');return}
    const blocks=tracked.map(item=>{
      const rows=Array.from({length:item.quantity||0},(_,i)=>{
        const unit=(item.units||[])[i]||{};
        return `<div class="unit-row"><span>${t('Xe {n}', {n:i+1})}</span><input name="frame" placeholder="${t('Số khung')}" value="${escape(unit.frameNumber||'')}" maxlength="30" autocomplete="off"><input name="engine" placeholder="${t('Số máy')}" value="${escape(unit.engineNumber||'')}" maxlength="30" autocomplete="off"></div>`;
      }).join('');
      return `<fieldset data-item="${item.id}"><legend>${escape(item.productName||'Xe')}${item.colorName?' · '+escape(item.colorName):''}</legend>${rows}</fieldset>`;
    }).join('');
    const panel=detailPanel('orderUnits',`<h2>${t('Số khung, số máy')}</h2><p class="muted">${t('Nhập cho từng xe trước khi bàn giao. Cần đủ mới bấm được')} "${isPickup(order)?t('Đã nhận'):t('Đang giao')}"${t(': xe phải có số trước khi rời cửa hàng. Số khung không trùng với xe nào khác.')}</p><form id="unitsForm" class="flow-form">${blocks}<button class="button compact">${t('Lưu số khung, số máy')}</button></form>`);
    panel.querySelector('#unitsForm').addEventListener('submit',async e=>{
      e.preventDefault();
      const units=[];
      e.target.querySelectorAll('fieldset').forEach(set=>set.querySelectorAll('.unit-row').forEach(row=>{
        const frameNumber=row.querySelector('[name=frame]').value.trim(),engineNumber=row.querySelector('[name=engine]').value.trim();
        if(frameNumber||engineNumber)units.push({orderItemId:Number(set.dataset.item),frameNumber,engineNumber});
      }));
      try{await send(`/api/v1/manager/orders/${order.id}/units`,'PUT',{units});tell(t('Đã lưu số khung, số máy.'));await load()}catch(err){tell(err.message,true)}
    });
  };
  const renderCancellation=order=>{
    let panel=document.querySelector('#cancellationDetails');
    if(order?.status!=='CANCELLED'||!order.cancellationReason){panel?.remove();return;}
    if(!panel){panel=document.createElement('section');panel.id='cancellationDetails';panel.className='data-panel';panel.style.marginTop='20px';document.querySelector('.work-main').appendChild(panel);}
    panel.innerHTML=`<h2>${t('Thông tin hủy đơn')}</h2><p><strong>${t('Lý do:')}</strong> ${escape(order.cancellationReason)}</p>${order.cancellationRequestedAt?`<p><strong>${t('Thời điểm hủy:')}</strong> ${escape(new Date(order.cancellationRequestedAt).toLocaleString('vi-VN'))}</p>`:''}`;
  };
  // ---- list filters (V5) ---------------------------------------------------------------
  // The manager's payment and customer lists filter on the server; the bar below only builds the query string.
  const paymentStatusOptions=[['',t('Mọi trạng thái')],['PENDING',t('Chờ thanh toán')],['PAID',t('Đã thanh toán')],['FAILED',t('Thất bại')],['CANCELLED',t('Đã hủy')],['REFUNDED',t('Đã hoàn tiền')]];
  const paymentMethodOptions=[['',t('Mọi phương thức')],['PAY_LATER',t('Thanh toán khi nhận xe')],['VNPAY','VNPay']];
  // "Hết hàng" is none left, "Sắp hết" is 3 or fewer. A product with colours can still have one colour sold out,
  // so the exact colour is shown in the stock editor.
  const LOW_STOCK=3;
  const stockStatus=n=>n<=0?'OUT':n<=LOW_STOCK?'LOW':'OK';
  const stockStatusText={OUT:t('Hết hàng'),LOW:t('Sắp hết'),OK:t('Còn hàng')};
  const stockFilter=()=>[{name:'stockStatus',client:true,options:[['',t('Mọi tình trạng')],['OUT',t('Hết hàng')],['LOW',t('Sắp hết (còn 3 xe trở xuống)')],['OK',t('Còn hàng')]],label:t('Tình trạng kho')}];
  const filterBars={
    '/manager/orders':[{name:'search',placeholder:t('Tìm mã đơn, email, người nhận, số khung hoặc số máy'),label:t('Tìm kiếm')}],
    '/manager/payments':[{name:'paymentStatus',options:paymentStatusOptions,label:t('Trạng thái thanh toán')},{name:'paymentMethod',options:paymentMethodOptions,label:t('Phương thức thanh toán')},{name:'search',placeholder:t('Tìm mã đơn, email, người nhận hoặc mã giao dịch'),label:t('Tìm kiếm')}],
    '/customer/payments':[{name:'paymentStatus',client:true,options:paymentStatusOptions,label:t('Trạng thái thanh toán')}],
    '/manager/inventory':stockFilter(),'/manager/products':stockFilter(),'/admin/inventory':stockFilter(),'/admin/products':stockFilter(),
    '/manager/customers':[{name:'search',placeholder:t('Tìm theo tên, email hoặc tài khoản'),label:t('Tìm kiếm')}],
    '/manager/reports':[{name:'period',options:[['day',t('Theo ngày')],['month',t('Theo tháng')],['quarter',t('Theo quý')],['year',t('Theo năm')]],label:t('Gộp theo')},{name:'from',type:'date',label:t('Từ ngày')},{name:'to',type:'date',label:t('Đến ngày')}]
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
      ?`<label>${escape(f.label)}<select name="${f.name}">${f.options.map(([v,optText])=>`<option value="${v}">${escape(optText)}</option>`).join('')}</select></label>`
      :`<label>${escape(f.label)}<input type="${f.type||'search'}" name="${f.name}" placeholder="${escape(f.placeholder||'')}" autocomplete="off"></label>`).join('')+'<button type="button" class="button compact ghost" id="clearFilters">' + t('Xóa bộ lọc') + '</button>';
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
  // Typing in a search box starts several requests; only the newest one may draw, or a slow earlier answer would replace it.
  let loadSeq=0;
  const load=async()=>{if(!api){loading.hidden=true;empty.hidden=true;return}const seq=++loadSeq;loading.hidden=false;empty.hidden=true;try{const data=await get(withFilters(api));if(seq!==loadSeq)return;if(secondaryApi)renderPromotions(await get(secondaryApi));if(mode==='dashboard'){renderMetrics(data);if(page==='/manager/reports'){document.querySelector('#panel-title').textContent=t('Doanh thu theo kỳ');renderTable(data?.breakdown||[]);renderRevenueChart(data?.breakdown||[]);let extra=document.querySelector('#report-products');if(!extra){extra=document.createElement('section');extra.id='report-products';extra.className='data-panel report-products';document.querySelector('.work-main').appendChild(extra)}extra.innerHTML=`<div class="report-columns"><div><p class="eyebrow">${t('KINETIC / SẢN PHẨM')}</p><h2>${t('Sản phẩm bán chạy')}</h2>${reportList(data?.topProducts||[])}</div><div><p class="eyebrow">${t('KINETIC / TỒN KHO')}</p><h2>${t('Sản phẩm bán chậm')}</h2>${reportList(data?.slowProducts||[])}</div></div>`;return}const rows=page==='/admin'?(data?.auditLogs||[]):page==='/manager'?(data?.recentOrders||[]):data;document.querySelector('#panel-title').textContent=page==='/admin'?t('Nhật ký hoạt động gần đây'):t('Đơn hàng gần đây');renderTable(Array.isArray(rows)?rows:[])}else if(mode==='profile'){loading.hidden=true;renderProfile(data||{});renderTable([])}else if(mode==='detail'){document.querySelector('#panel-title').textContent=t('Thông tin đơn hàng');renderTable([data]);renderTimeline(data);renderOrderItems(data);renderPricing(data);renderDelivery(data.shipment);renderManagerControls(data);renderUnitsForm(data);renderPickupDeadline(data);renderCancellation(data)}else if(page==='/favorites'){document.querySelector('#panel-title').textContent=t('Xe bạn đã lưu');renderFavorites(data)}else{document.querySelector('#panel-title').textContent=mode==='inventory'?t('Danh mục xe và tồn kho'):mode==='pricing'?t('Quy tắc giá hiện hành'):t('Danh sách');renderTable(clientFiltered(data))}}catch(err){loading.hidden=true;tell(err.message,true)}};
  document.querySelector('#refresh')?.addEventListener('click',load);document.querySelector('#logout')?.addEventListener('click',async()=>{try{await send('/api/v1/auth/logout','POST')}finally{location.href='/'}});
  table?.addEventListener('click',async e=>{const b=e.target.closest('[data-action]');if(!b)return;const id=b.dataset.id;try{if(b.dataset.action==='pay-vnpay'){b.disabled=true;try{const payment=await send('/api/v1/payments/vnpay/create','POST',{orderId:Number(id),language:'vn'});location.href=payment.paymentUrl}finally{b.disabled=false}return}if(b.dataset.action==='confirm-payment')await send(`/api/v1/manager/payments/${id}/confirm`,'PATCH',{});else if(b.dataset.action==='refund-payment'){const note=prompt(t('Ghi chú hoàn tiền (mã giao dịch hoàn, ngày chuyển...)'));if(note===null)return;await send(`/api/v1/manager/payments/${id}/refund`,'PATCH',{note})}else if(b.dataset.action==='stock'){if(b.dataset.slug&&window.KineticStock){const detail=await get(`/api/v1/products/${encodeURIComponent(b.dataset.slug)}`);if((detail.variants||[]).length){await window.KineticStock.open(detail,send,tell);await load();return}}const stockQuantity=Number(prompt(t('Nhập số lượng tồn kho mới'),b.dataset.stock));if(!Number.isInteger(stockQuantity)||stockQuantity<0)return;await send(`/api/v1/manager/products/${id}/stock`,'PATCH',{stockQuantity})}else if(b.dataset.action==='account-status'){const status=b.dataset.status==='ACTIVE'?'INACTIVE':'ACTIVE';await send(`/api/v1/admin/accounts/${id}/status`,'PATCH',{status})}else if(b.dataset.action==='pricing'){const amount=Number(prompt(t('Số tiền áp dụng'),b.dataset.amount));if(!Number.isFinite(amount)||amount<0)return;await send(`/api/v1/admin/pricing-rules/${id}`,'PATCH',{amount,status:b.dataset.status})}else if(b.dataset.action==='support-status'){const note=prompt(t('Ghi chú xử lý (có thể để trống)'));if(note===null)return;await send(`/api/v1/manager/support-requests/${id}`,'PATCH',{status:b.dataset.status,staffNote:note})}else if(b.dataset.action==='read')await send(`/api/v1/notifications/${id}/read`,'PATCH',{});else if(b.dataset.action==='cancel-order'){const reason=prompt(t('Lý do hủy đơn hàng'));if(reason===null)return;if(!reason.trim()){tell(t('Vui lòng nhập lý do hủy đơn hàng.'),true);return}await send(`/api/v1/orders/${id}/cancellation-request`,'POST',{reason:reason.trim()});tell(t('Đã hủy đơn hàng.'));await load();return}else if(b.dataset.action==='remove-favorite')await send(`/api/v1/favorites/${id}`,'DELETE');else return;tell(t('Đã cập nhật dữ liệu.'));await load()}catch(err){tell(err.message,true)}});
  table?.addEventListener('change',async e=>{const s=e.target.closest('[data-action="order-status"]');if(!s||!s.value)return;try{if(await changeOrderStatus(s.dataset.id,s.value))tell(t('Trạng thái đơn hàng đã cập nhật.'));await load()}catch(err){tell(err.message,true)}});
  // Guest orders are attached only after the customer proves they own the account email with an OTP.
  const linkButton=document.querySelector('#link-guest-orders');
  if(page==='/customer/orders'&&linkButton){
    linkButton.hidden=false;
    linkButton.addEventListener('click',async()=>{
      linkButton.disabled=true;
      try{
        const profile=await get('/api/v1/auth/profile');
        const email=profile?.email;
        if(!email){tell(t('Tài khoản chưa có email để xác minh.'),true);return}
        if(!confirm(`${t('Hệ thống sẽ gửi mã xác minh tới')} ${email} ${t('để tìm các đơn bạn đã đặt bằng email này khi chưa đăng nhập. Tiếp tục?')}`))return;
        let sent;
        try{sent=await send('/api/v1/orders/email-verification/send','POST',{email})}
        catch(err){tell(t('Chưa gửi được mã xác minh qua email. Vui lòng thử lại sau hoặc liên hệ cửa hàng.'),true);return}
        const code=prompt(`${t('Nhập mã gồm 6 chữ số đã gửi tới')} ${email}`);
        if(code===null)return;
        if(!/^\d{6}$/.test(code.trim())){tell(t('Mã xác minh gồm 6 chữ số.'),true);return}
        await send('/api/v1/orders/email-verification/verify','POST',{verificationSessionId:sent.verificationSessionId,code:code.trim()});
        const result=await send('/api/v1/orders/guest-orders/link','POST',{emailVerificationSessionId:sent.verificationSessionId});
        tell(result.linkedOrders?`${t('Đã liên kết')} ${result.linkedOrders} ${t('đơn hàng vào tài khoản.')}`:t('Không tìm thấy đơn nào đặt bằng email này khi chưa đăng nhập.'));
        await load();
      }catch(err){tell(err.message,true)}
      finally{linkButton.disabled=false}
    });
  }
  if(page==='/admin/accounts'){const b=document.querySelector('#create-account');if(b)b.hidden=false}window.addEventListener('workspace:reload',()=>load());setupFilters();load();
})();
