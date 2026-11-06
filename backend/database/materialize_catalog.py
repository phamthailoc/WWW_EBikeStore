"""Build the reviewed electric-only catalog and a non-destructive live DB update."""
from pathlib import Path
import argparse
import copy
import json
import re
import unicodedata

ROOT=Path(__file__).resolve().parents[2]
CATALOG=ROOT/'backend/database/catalog'
SOURCE=ROOT/'backend/data-backup/dataCawl/data/products_db_ready_clean.json'
if not SOURCE.exists(): SOURCE=ROOT/'archive/catalog-before-correction/dataCawl/data/products_db_ready_clean.json'

def ascii_text(value):
    return ''.join(c for c in unicodedata.normalize('NFD',value.lower().replace('đ','d')) if not unicodedata.combining(c))

def q(value):
    if value is None:return 'NULL'
    if isinstance(value,bool):return '1' if value else '0'
    if isinstance(value,(float,int)):return str(value)
    return "'"+str(value).replace('\\','\\\\').replace("'","''")+"'"

def numeric(value):
    value=re.sub(r'(?<=\d)\.(?=\d{3}(?:\D|$))','',value)
    return [float(v.replace(',','.')) for v in re.findall(r'\d+(?:[.,]\d+)?',value)]

def specs_for(item,source,bike):
    spec={k:None for k in item['specification']}
    spec.update(model_code=item['specification']['model_code'],brand='DIBAO',vehicle_type='E_BIKE' if bike else 'E_SCOOTER')
    for row in source['specification_rows']:
        if len(row)<2:continue
        key=ascii_text(row[-2]);value=row[-1];normalized=ascii_text(value);numbers=numeric(value)
        if ('cong suat' in key or ('dong co dien' in key and 'cong' in key)) and 'w' in normalized and numbers:
            spec['motor_power_watts']=int(max(numbers));spec['peak_motor_power_watts']=int(max(numbers))
        elif 'toc do' in key or 'toc đo' in key:
            match=re.search(r'([\d\s.,-]+)\s*km',normalized)
            if match:spec['max_speed_kmh']=max(numeric(match.group(1)))
        elif 'quang duong' in key:
            match=re.search(r'([\d\s.,-]+)\s*km',normalized)
            if match:spec['max_range_km']=max(numeric(match.group(1)))
        elif ('binh dien' in key or key=='ac quy') and re.search(r'\d+\s*v|\d+\s*ah|ac quy|lithium|chi',normalized):
            spec['battery_type']='LEAD_ACID' if 'ac quy' in normalized or 'chi' in normalized else ('LITHIUM_ION' if 'lithium' in normalized else None)
            capacity=re.search(r'(\d+(?:[.,]\d+)?)\s*\(?\s*ah',normalized)
            voltage=re.search(r'(\d+)\s*v',normalized)
            if capacity:spec['battery_capacity_ah']=float(capacity.group(1).replace(',','.'))
            if voltage:spec['battery_voltage_v']=int(voltage.group(1))
        elif 'thoi gian' in key and ('sac' in key or 'xac' in key) and numbers:spec['charging_time_hours']=max(numbers)
        elif 'bao hanh' in key and numbers:spec['warranty_months']=int(max(numbers))
        elif 'tai trong' in key and numbers:spec['max_load_kg']=max(numbers)
        elif 'trong luong' in key and numbers:spec['product_weight_kg']=max(numbers)
        elif 'kich thuoc' in key:spec['dimensions_mm']=value
        elif 'phanh' in key:spec['brake_type']='DISC' if 'dia' in normalized else 'DRUM'
    return spec

def main(write_update=False):
    original=json.loads(SOURCE.read_text(encoding='utf-8'))
    manifest=json.loads((CATALOG/'source-manifest.json').read_text(encoding='utf-8'))
    reviews=json.loads((CATALOG/'review.json').read_text(encoding='utf-8'))['colors']
    by_slug={i['slug']:i for i in manifest['products']}
    retained=[];all_slugs=[p['product']['slug'] for p in original['products']]
    for old in original['products']:
        slug=old['product']['slug']
        if slug in manifest['excluded_gasoline']:continue
        source=by_slug[slug];item=copy.deepcopy(old);p=item['product']
        p['name']=p['name'].replace('KINETIC','DIBAO')
        bike=old['category']['slug']=='xe-dap-dien' or 'ban dap' in ascii_text(source['source_title'])
        item['category']={'name':'Xe đạp điện' if bike else 'Xe điện','slug':'xe-dap-dien' if bike else 'xe-dien','description':'Xe đạp điện có bàn đạp.' if bike else 'Xe sử dụng động cơ điện.','vehicle_type':'E_BIKE' if bike else 'E_SCOOTER'}
        item['specification']=specs_for(item,source,bike)
        p['description']=p['name']+' là mẫu '+('xe đạp điện có bàn đạp' if bike else 'xe sử dụng động cơ điện')+' của DIBAO, phù hợp cho di chuyển hằng ngày. Chọn màu để xem đúng ảnh và tình trạng của phiên bản.'
        p['is_active']=True
        item['source_url']=source['source_url'];item['source_title']=source['source_title']
        item['variants']=[];item['images']=[]
        for number,(name,hex_color,indices) in enumerate(reviews[slug],1):
            sku=slug+f'-verified-c{number:02}'
            stock=p['stock_quantity']//len(reviews[slug])+(1 if number<=p['stock_quantity']%len(reviews[slug]) else 0)
            item['variants'].append({'sku':sku,'variant_name':name,'color_name':name,'color_hex':hex_color,'battery_capacity_ah':item['specification']['battery_capacity_ah'],'additional_price':0,'stock_quantity':stock,'is_default':number==1,'is_active':True})
            for position,index in enumerate(indices):
                image=next(im for im in source['images'] if im['index']==index)
                item['images'].append({'variant_sku':sku,'image_url':image['path'],'alt_text':p['name']+' - '+name,'sort_order':(number-1)*20+position,'is_primary':number==1 and position==0,'source_url':image['source_url'],'sha256':image['sha256']})
        retained.append(item)
    result={'metadata':{'source':'https://dibao.com.vn/xe-may-dien','reviewed_on':'2026-11-02','review_method':'Manufacturer photos visually grouped by actual body color; original photos are preserved. Store prices are retained as project data.','excluded_gasoline':manifest['excluded_gasoline']},'products':retained}
    (CATALOG/'products.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    if not write_update:
        print('Reviewed products:',len(retained),'colors:',sum(len(i['variants']) for i in retained),'photos:',sum(len(i['images']) for i in retained))
        return
    sql=['-- Reviewed electric-only catalog. Preserves orders, IDs, stock and legacy variants.\nSET NAMES utf8mb4;\nSTART TRANSACTION;\n']
    excluded=','.join(q(s) for s in manifest['excluded_gasoline'])
    known=','.join(q(s) for s in all_slugs)
    sql.append(f'UPDATE products SET is_active=0,is_featured=0 WHERE slug IN ({excluded});\n')
    sql.append(f'UPDATE product_variants v JOIN products p ON p.id=v.product_id SET v.is_active=0,v.is_default=0 WHERE p.slug IN ({known}) AND v.sku NOT LIKE \'%-verified-c%\';\n')
    sql.append(f'UPDATE product_images i JOIN products p ON p.id=i.product_id SET i.status=\'DELETED\',i.is_primary=0 WHERE p.slug IN ({known}) AND i.image_url NOT LIKE \'/images/catalog/%\';\n')
    for item in retained:
        p=item['product'];spec=item['specification'];slug=q(p['slug']);c=item['category']
        sql.append('UPDATE products SET name='+q(p['name'])+',description='+q(p['description'])+',is_active=1,category_id=(SELECT id FROM categories WHERE slug='+q(c['slug'])+') WHERE slug='+slug+';\n')
        sql.append('UPDATE product_specifications s JOIN products p ON p.id=s.product_id SET '+','.join('s.'+k+'='+q(v) for k,v in spec.items())+' WHERE p.slug='+slug+';\n')
        count=len(item['variants'])
        for index,v in enumerate(item['variants']):
            cols=['sku','variant_name','color_name','color_hex','battery_capacity_ah','additional_price']
            stock=f'FLOOR(p.stock_quantity/{count})+IF(MOD(p.stock_quantity,{count})>{index},1,0)'
            sql.append('INSERT INTO product_variants(product_id,'+','.join(cols)+',stock_quantity,is_default,is_active) SELECT p.id,'+','.join(q(v[k]) for k in cols)+','+stock+','+q(v['is_default'])+',1 FROM products p WHERE p.slug='+slug+' ON DUPLICATE KEY UPDATE variant_name=VALUES(variant_name),color_name=VALUES(color_name),color_hex=VALUES(color_hex),is_default=VALUES(is_default),is_active=1;\n')
        for im in item['images']:
            image=q(im['image_url']);sku=q(im['variant_sku'])
            sql.append('INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'+','.join(q(im[k]) for k in ['image_url','alt_text','sort_order','is_primary'])+",'LOCAL','ACTIVE' FROM products p JOIN product_variants v ON v.product_id=p.id AND v.sku="+sku+' WHERE p.slug='+slug+' AND NOT EXISTS (SELECT 1 FROM product_images i WHERE i.product_id=p.id AND i.variant_id=v.id AND i.image_url='+image+');\n')
            sql.append("UPDATE product_images i JOIN products p ON p.id=i.product_id JOIN product_variants v ON v.id=i.variant_id SET i.status='ACTIVE',i.alt_text="+q(im['alt_text'])+',i.sort_order='+q(im['sort_order'])+',i.is_primary='+q(im['is_primary'])+' WHERE p.slug='+slug+' AND v.sku='+sku+' AND i.image_url='+image+';\n')
    sql.append('COMMIT;\n')
    updates=ROOT/'backend/database/updates';updates.mkdir(exist_ok=True)
    (updates/'20261102_verified_electric_catalog.sql').write_text(''.join(sql),encoding='utf-8')
    print('Reviewed products:',len(retained),'colors:',sum(len(i['variants']) for i in retained),'photos:',sum(len(i['images']) for i in retained),'bicycles:',sum(i['category']['slug']=='xe-dap-dien' for i in retained))

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--write-update',action='store_true',help='Explicitly generate an incremental SQL update for an existing database.')
    args=parser.parse_args()
    main(write_update=args.write_update)
