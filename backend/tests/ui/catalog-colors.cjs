const {chromium}=require('playwright');
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const root=path.resolve(__dirname,'../../..');
const source=JSON.parse(fs.readFileSync(path.join(root,'backend/database/catalog/products.json'),'utf8'));
const base=process.env.BASE_URL||'http://localhost:8080';
const out=path.join(root,'backend/target/catalog-verification');fs.mkdirSync(out,{recursive:true});
const report={products:0,colors:0,photos:0,checks:[],errors:[],screenshots:[]};
const chrome=process.env.CHROME_PATH||['C:/Program Files/Google/Chrome/Application/chrome.exe','C:/Program Files (x86)/Google/Chrome/Application/chrome.exe'].find(fs.existsSync);
(async()=>{
  const browser=await chromium.launch({executablePath:chrome,headless:true});
  try{
    const context=await browser.newContext({viewport:{width:1440,height:1100}}),page=await context.newPage();
    page.on('pageerror',error=>report.errors.push(error.message));
    const catalog=await(await context.request.get(base+'/api/v1/products')).json();assert.equal(catalog.length,source.products.length);
    for(const slug of source.metadata.excluded_gasoline){assert.ok(!catalog.some(p=>p.slug===slug));assert.equal((await context.request.get(base+'/api/v1/products/'+slug)).status(),404);}
    report.checks.push('Gasoline models absent from catalog and cannot be opened');
    for(const p of source.products){
      const live=await(await context.request.get(base+'/api/v1/products/'+p.product.slug)).json();
      assert.equal(live.specification.brand,'DIBAO');assert.equal(live.category.slug,p.category.slug);assert.equal(live.variants.length,p.variants.length);
      assert.ok(['E_SCOOTER','E_BIKE'].includes(live.specification.vehicleType));
      await page.goto(base+'/product/'+p.product.slug);await page.locator('.variant-option').first().waitFor();
      assert.equal(await page.locator('.variant-option').count(),p.variants.length);
      const seenHashes=new Map();
      for(const variant of p.variants){
        const expected=p.images.filter(im=>im.variant_sku===variant.sku),actual=live.variants.find(v=>v.sku===variant.sku);
        assert.equal(actual.colorName,variant.color_name);assert.equal(actual.colorHex,variant.color_hex);assert.deepEqual(actual.images,expected.map(im=>im.image_url));
        for(const im of expected){
          const file=path.join(root,'backend/src/main/resources/static',im.image_url);
          const hash=crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');assert.equal(hash,im.sha256);
          if(seenHashes.has(hash))assert.equal(seenHashes.get(hash),variant.sku,'Identical image cannot represent different colors');seenHashes.set(hash,variant.sku);report.photos++;
        }
        const button=page.locator('.variant-option').filter({has:page.locator(`img[src="${actual.imageUrl}"]`)});
        await button.click();assert.equal(await page.locator('#selectedColor').textContent(),variant.color_name);
        assert.equal(await page.locator('#productImage').getAttribute('src'),actual.imageUrl);
        await page.waitForFunction(()=>{const i=document.querySelector('#productImage');return i.complete&&i.naturalWidth>0});
        assert.equal(await page.locator('#productGallery button').count(),expected.length);
        if(expected.length>1){await page.locator('#productGallery button').last().click();assert.equal(await page.locator('#productImage').getAttribute('src'),expected.at(-1).image_url);assert.equal(await page.locator('#selectedColor').textContent(),variant.color_name);}
        report.colors++;
      }
      if(['kinetic-amy','kinetic-gogo-cross','kinetic-keva','kinetic-pansy-s2-cang-nhom'].includes(p.product.slug)){
        await page.evaluate(()=>scrollTo(0,0));await page.screenshot({path:path.join(out,p.product.slug+'.png'),fullPage:true});report.screenshots.push(p.product.slug+'.png');
      }
      report.products++;console.log(p.product.slug,live.variants.length,'colors OK');
    }
    await page.goto(base+'/product/kinetic-gogo-cross');await page.getByRole('button',{name:'Đỏ',exact:true}).click();await page.locator('#addToCart').click();
    const saved=await page.evaluate(()=>JSON.parse(localStorage.getItem('kinetic_cart'))[0]);assert.equal(saved.colorName,'Đỏ');assert.equal(saved.image,await page.locator('.variant-option.selected').getAttribute('data-image'));
    await page.goto(base+'/cart');await page.locator('.cart-item').first().waitFor();assert.ok((await page.locator('.cart-item-info').textContent()).includes('Đỏ'));assert.equal(await page.locator('.cart-item-photo img').getAttribute('src'),saved.image);
    report.checks.push('Selected color, variant ID and photo stay aligned in cart');
    await page.goto(base+'/product/kinetic-keva');await page.setViewportSize({width:390,height:844});await page.getByRole('button',{name:'Vàng xanh',exact:true}).click();
    assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));await page.evaluate(()=>scrollTo(0,0));await page.screenshot({path:path.join(out,'color-selector-mobile.png'),fullPage:true});
    report.checks.push('Two-tone photo choices fit mobile width');assert.equal(report.errors.length,0);report.status='passed';
  }catch(error){report.status='failed';report.failure=error.stack;throw error;}
  finally{fs.writeFileSync(path.join(out,'report.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1});
