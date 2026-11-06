const {chromium} = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const base = process.env.BASE_URL || 'http://localhost:8080';
const output = path.resolve(__dirname, '../../target/home-layout');
fs.mkdirSync(output, {recursive:true});
const report = {checks:[], errors:[], screenshots:[]};
const chrome = process.env.CHROME_PATH || ['C:/Program Files/Google/Chrome/Application/chrome.exe', 'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe'].find(fs.existsSync);
const check = (name, value) => { assert.ok(value, name); report.checks.push(name); };

(async () => {
  const browser = await chromium.launch({executablePath:chrome, headless:true});
  try {
    const page = await browser.newPage();
    page.on('pageerror', error => report.errors.push(error.message));
    page.on('response', response => {if(response.status() >= 400 && response.url().startsWith(base)) report.errors.push(`${response.status()} ${response.url()}`);});
    for (const width of [1920, 1440, 1024, 768, 390, 360]) {
      await page.setViewportSize({width, height:1000});
      check(`Home HTTP 200 at ${width}px`, (await page.goto(base)).status() === 200);
      await page.evaluate(() => document.fonts.ready);
      const layout = await page.evaluate(() => {
        const rect = selector => {const r = document.querySelector(selector).getBoundingClientRect(); return {left:r.left, right:r.right, width:r.width};};
        return {
          scroll:document.documentElement.scrollWidth,
          columns:getComputedStyle(document.querySelector('.principle-grid')).gridTemplateColumns.split(' ').length,
          technology:rect('.principles'), technologyTitle:rect('.principles h2'), newsletter:rect('.newsletter'), newsletterTitle:rect('.newsletter h2'),
          email:rect('#newsletterEmail'), button:rect('#newsletter button'),
          imageFit:getComputedStyle(document.querySelector('.product-image img')).objectFit,
          links:[...document.querySelectorAll('.top a')].map(e => {const r=e.getBoundingClientRect();return {x:r.x,right:r.right};})
        };
      });
      check(`No horizontal overflow at ${width}px`, layout.scroll <= width + 1);
      check(`Technology has ${width <= 720 ? 1 : 3} columns at ${width}px`, layout.columns === (width <= 720 ? 1 : 3));
      check(`Technology and newsletter have side gutters at ${width}px`, layout.technologyTitle.left >= width * .06 && layout.newsletterTitle.left >= width * .06);
      check(`Newsletter email has usable width at ${width}px`, layout.email.width >= 160 && layout.email.right <= layout.button.left + 1 && layout.button.right < width);
      check(`Navigation stays inside viewport at ${width}px`, layout.links.every(r=>r.x>=0 && r.right<=width+1));
      check(`Vehicle images are contained at ${width}px`, layout.imageFit === 'contain');
      await page.locator('.top a[href="#technology"]').click();
      await page.waitForFunction(() => {const r=document.querySelector('#technology').getBoundingClientRect();return r.top>=0&&r.top<innerHeight;});
      check(`Technology menu opens correct section at ${width}px`, new URL(page.url()).hash === '#technology');
      for (const section of ['principles', 'newsletter']) {
        await page.locator(`.${section}`).scrollIntoViewIfNeeded();
        await page.waitForTimeout(250);
        const name = `${section}-${width}.png`;
        await page.screenshot({path:path.join(output,name)});
        report.screenshots.push(name);
      }
    }
    // Read-only smoke checks for pages sharing the changed navigation/styles.
    for (const width of [1440, 390, 360]) {
      await page.setViewportSize({width,height:1000});
      for (const route of ['/products','/product/kinetic-keva','/login','/signup','/cart','/favorites','/compare']) {
        check(`${route} HTTP 200 at ${width}px`, (await page.goto(base+route)).status() === 200);
        await page.waitForTimeout(150);
        const scrollWidth = await page.evaluate(()=>document.documentElement.scrollWidth);
        check(`${route} no horizontal overflow at ${width}px`, scrollWidth <= width+1);
      }
    }
    check('No JavaScript errors or failing local resources', report.errors.length === 0);
  } finally {
    fs.writeFileSync(path.join(output,'report.json'),JSON.stringify(report,null,2));
    await browser.close();
  }
  console.log(`${report.checks.length} checks passed; screenshots and report: ${output}`);
})().catch(error=>{console.error(error);process.exitCode=1;});
