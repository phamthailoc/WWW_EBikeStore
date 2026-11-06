const {chromium}=require('playwright'),fs=require('node:fs'),path=require('node:path');
const chrome=process.env.CHROME_PATH||['C:/Program Files/Google/Chrome/Application/chrome.exe','C:/Program Files (x86)/Google/Chrome/Application/chrome.exe'].find(fs.existsSync);
let context;
(async()=>{
  context=await chromium.launchPersistentContext(path.resolve(__dirname,'../../target/catalog-preview-profile'),{executablePath:chrome,headless:false,viewport:{width:1440,height:1100}});
  const base=process.env.BASE_URL||'http://localhost:8080';
  const page=context.pages()[0]||await context.newPage();await page.goto(base+'/product/kinetic-keva');
  await page.getByRole('button',{name:'Vàng xanh',exact:true}).click();
  const other=await context.newPage();await other.goto(base+'/product/kinetic-gogo-cross');await other.getByRole('button',{name:'Đỏ',exact:true}).click();
  const catalog=await context.newPage();await catalog.goto(base+'/products');await page.bringToFront();
  console.log('Chrome ready: Keva two-tone colors, Gogo Cross color gallery and electric-only catalog.');
  await new Promise(resolve=>context.on('close',resolve));
})().catch(async error=>{console.error(error);await context?.close();process.exitCode=1});
