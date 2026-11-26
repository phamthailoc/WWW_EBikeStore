const B='http://localhost:8088'; const em='qa.otp.'+Date.now()+'@example.com';
const p=(u,b)=>fetch(B+u,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(b)}).then(async r=>[r.status,await r.json()]);
const [s,j]=await p('/api/v1/orders/email-verification/send',{email:em}); const sid=j.verificationSessionId; console.log('send',s);
const [s2]=await p('/api/v1/orders/email-verification/send',{email:em}); console.log('resend at once',s2);
for(let i=1;i<=5;i++){const [st,b]=await p('/api/v1/orders/email-verification/verify',{verificationSessionId:sid,code:'00000'+i}); console.log('wrong',i,st,b.message);}
await new Promise(r=>setTimeout(r,1500));
const mails=await (await fetch('http://127.0.0.1:2526/mails')).json(); const m=mails.filter(x=>x.to.join().includes(em)); const code=m[0].text.match(/\b(\d{6})\b/)[1];
const [s3,b3]=await p('/api/v1/orders/email-verification/verify',{verificationSessionId:sid,code}); console.log('right code after 5 wrong',s3,JSON.stringify(b3).slice(0,120), 'mails to this email:',m.length);
