const {chromium} = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const base = process.env.BASE_URL || 'http://localhost:8080';
const output = path.resolve(__dirname, '../../target/ui-tests');
fs.mkdirSync(output, {recursive:true});
const report = {checks:[],orders:[],screenshots:[],pageErrors:[]};
const check = (name, condition) => {assert.ok(condition,name);report.checks.push(name);};
const chrome = process.env.CHROME_PATH || ['C:/Program Files/Google/Chrome/Application/chrome.exe','C:/Program Files (x86)/Google/Chrome/Application/chrome.exe'].find(fs.existsSync);

(async()=>{
  const browser = await chromium.launch({executablePath:chrome,headless:true});
  try{
    const context=await browser.newContext({viewport:{width:1440,height:1100}});
    const page=await context.newPage();
    page.on('pageerror',error=>report.pageErrors.push(error.message));
    const catalog=await (await context.request.get(base+'/api/v1/products')).json();
    const candidates=catalog.filter(p=>p.stockQuantity>=8).slice(0,8);
    const details=[];
    for(const p of candidates){const d=await (await context.request.get(base+'/api/v1/products/'+p.slug)).json();if(d.variants.filter(v=>v.stockQuantity>=5).length>=2)details.push(d);if(details.length===2)break;}
    assert.equal(details.length,2,'Need two stocked products with multiple colors for UI tests');
    const [first,second]=details;
    const cart=()=>page.evaluate(()=>JSON.parse(localStorage.getItem('kinetic_cart')||'[]'));
    async function screenshot(name){await page.evaluate(()=>scrollTo(0,0));await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));await page.screenshot({path:path.join(output,name),fullPage:true});report.screenshots.push(name);}
    async function contacts(){await page.locator('[name="customerName"]').fill('Khách Kiểm Thử');await page.locator('[name="phoneNumber"]').fill('0901234567');await page.locator('[name="customerEmail"]').fill('cart.qa@example.com');}
    async function order(){
      const responsePromise=page.waitForResponse(r=>r.url()===base+'/api/v1/orders'&&r.request().method()==='POST');
      await page.locator('#placeOrder').click();const response=await responsePromise;
      assert.equal(response.status(),201,response.status()===201?'':await response.text());
      await page.waitForURL('**/checkout/success');
      const data=await page.evaluate(()=>JSON.parse(localStorage.getItem('kinetic_last_order')));
      report.orders.push({id:data.id,orderNumber:data.orderNumber,total:data.totalAmount,delivery:data.shipment?.deliveryMethod,address:data.shipment?.shippingAddress,items:data.items.length});
      return data;
    }
    await page.goto(base+'/product/'+first.slug);
    await page.locator('#productQuantity').fill('2');await page.locator('#productQuantity').dispatchEvent('change');
    await page.locator('#addToCart').click();
    check('Add selected quantity', (await cart())[0].quantity===2);
    await page.locator('#addToCart').click();check('Same color merges into one line',(await cart()).length===1&&(await cart())[0].quantity===4);
    const buttons=page.locator('.variant-option');await buttons.nth(1).click();
    await page.locator('#productQuantity').fill('1');await page.locator('#productQuantity').dispatchEvent('change');await page.locator('#addToCart').click();
    check('Different color remains a separate cart line',(await cart()).length===2);
    await page.reload();check('Cart persists on reload',(await cart()).length===2);
    await page.locator('.purchase-controls').scrollIntoViewIfNeeded();await screenshot('product-cart-controls.png');
    await page.goto(base+'/cart');await page.locator('.cart-item').first().waitFor();
    await page.locator('.cart-item').first().locator('[data-quantity="minus"]').click();
    check('Cart decrement updates stored quantity',(await cart())[0].quantity===3);
    await page.locator('.cart-item').first().locator('[data-quantity="plus"]').click();
    check('Cart increment updates stored quantity',(await cart())[0].quantity===4);
    await page.locator('.cart-item').nth(1).locator('.remove-cart-item').click();
    check('Remove only the selected color',(await cart()).length===1);
    await page.locator('.cart-quantity').fill('99');await page.locator('.cart-quantity').dispatchEvent('change');
    check('Quantity above stock/order limit is rejected',(await cart())[0].quantity===4);
    await page.locator('.cart-quantity').fill('1');await page.locator('.cart-quantity').dispatchEvent('change');
    await page.goto(base+'/product/'+second.slug);await page.locator('#addToCart').click();
    const before=await cart();check('Cart supports different products',before.length===2);
    await page.goto(base+'/cart');await page.locator('.cart-item').nth(1).waitFor();await screenshot('cart-desktop.png');

    await page.goto(base+'/product/'+second.slug);await page.locator('#buyNow').click();await page.waitForURL('**/checkout?buyNow=1');
    await page.locator('#checkoutForm').waitFor();await page.waitForFunction(()=>!document.querySelector('#placeOrder').disabled);
    check('Buy now preserves existing cart',JSON.stringify((await cart()).map(i=>[i.id,i.variantId,i.quantity]))===JSON.stringify(before.map(i=>[i.id,i.variantId,i.quantity])));
    check('Home delivery shown by default',await page.locator('#homeDeliveryFields').isVisible());
    check('Store fields hidden for home delivery',!await page.locator('#storePickupFields').isVisible());
    check('Province is required',await page.locator('[name="province"]').evaluate(el=>el.required));
    check('Home address is required',await page.locator('[name="detailedAddress"]').evaluate(el=>el.required));
    await contacts();await page.locator('[name="province"]').selectOption('Hồ Chí Minh');
    await page.locator('[name="detailedAddress"]').fill('123 Nguyễn Trãi, Phường Bến Thành');
    await page.locator('[name="notes"]').fill('[UI CART] Home delivery smoke test');
    const quote=page.waitForResponse(r=>r.url().endsWith('/api/v1/orders/quote'));
    await page.locator('[name="includeRegistrationService"]').check();
    const serviceQuote=await (await quote).json();check('Registration service adds explicit fee',Number(serviceQuote.registrationFee)===2500000&&Number(serviceQuote.discountAmount)===0);
    await page.locator('[name="includeRegistrationService"]').uncheck();
    await page.locator('#discountCode').fill('INVALID');await page.locator('#applyPromo').click();
    await page.locator('#checkoutQuote .commerce-error').waitFor();check('Invalid code disables ordering',await page.locator('#placeOrder').isDisabled());
    await page.locator('#discountCode').fill('EBIKE100');
    const promo=page.waitForResponse(r=>r.url().endsWith('/api/v1/orders/quote'));await page.locator('#applyPromo').click();
    const promoData=await (await promo).json();check('Valid promo reduces only the code amount',Number(promoData.discountAmount)===100000);
    await page.locator('#discountCode').fill('');
    const noPromo=page.waitForResponse(r=>r.url().endsWith('/api/v1/orders/quote'));await page.locator('#applyPromo').click();
    const plain=await (await noPromo).json();check('Empty code has no automatic discount',Number(plain.discountAmount)===0&&Number(plain.totalAmount)===Number(plain.subtotal));
    await page.waitForFunction(()=>!document.querySelector('#placeOrder').disabled);
    await screenshot('checkout-home.png');
    const homeOrder=await order();check('Home order saves mode and province',homeOrder.shipment.deliveryMethod==='HOME_DELIVERY'&&homeOrder.shipment.province==='Hồ Chí Minh'&&!homeOrder.shipment.pickupShowroom);
    check('Buy now success preserves cart items',(await cart()).length===2);
    await screenshot('order-home-success.png');

    await page.goto(base+'/cart');await page.locator('#checkoutCart').click();await page.waitForURL('**/checkout');
    await page.locator('#checkoutForm').waitFor();await contacts();
    await page.locator('[value="STORE_PICKUP"]').check();
    check('Pickup hides and disables home address',!await page.locator('#homeDeliveryFields').isVisible()&&await page.locator('[name="province"]').isDisabled());
    await page.locator('#showroomSearch').fill('no-store-matching-xyz');await page.locator('.showroom-empty').waitFor();
    check('Showroom search shows no-results state',await page.locator('.showroom-option').count()===0);
    const stores=await (await context.request.get(base+'/api/v1/showrooms')).json();
    await page.locator('#showroomSearch').fill(stores[0].name);await page.locator('.showroom-option').first().waitFor();
    await page.locator('[name="pickupShowroomId"]').first().check();
    await page.locator('[name="notes"]').fill('[UI CART] Store pickup smoke test');
    await page.waitForFunction(()=>!document.querySelector('#placeOrder').disabled);await screenshot('checkout-pickup.png');
    const pickup=await order();check('Pickup order stores selected showroom and multiple products',pickup.shipment.deliveryMethod==='STORE_PICKUP'&&!!pickup.shipment.pickupShowroom&&pickup.items.length===2);
    check('Purchased cart lines removed after successful order',(await cart()).length===0);
    await screenshot('order-pickup-success.png');await page.goto(base+'/cart');await page.locator('.commerce-empty').waitFor();check('Empty cart has a recovery link',await page.locator('.commerce-empty a[href="/products"]').count()===1);

    await page.goto(base+'/product/'+first.slug);await page.locator('#addToCart').click();
    await page.setViewportSize({width:390,height:844});await page.locator('.purchase-controls').scrollIntoViewIfNeeded();await screenshot('product-mobile.png');
    check('Product page fits mobile width',await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));
    await page.goto(base+'/cart');await page.locator('.cart-item').waitFor();await screenshot('cart-mobile.png');
    check('Cart fits mobile width',await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));
    await page.locator('#checkoutCart').click();await page.locator('#checkoutForm').waitFor();await screenshot('checkout-mobile.png');
    check('Checkout fits mobile width',await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));
    check('No uncaught browser JavaScript errors',report.pageErrors.length===0);
    report.status='passed';
  }catch(error){report.status='failed';report.error=error.stack;throw error;}
  finally{fs.writeFileSync(path.join(output,'report.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
