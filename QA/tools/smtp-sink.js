// Minimal fake SMTP server for QA: accepts every mail, keeps it in memory and on disk,
// and serves the list as JSON on http://127.0.0.1:<httpPort>/mails (DELETE clears it).
const net = require('net');
const http = require('http');
const fs = require('fs');
const path = require('path');

const smtpPort = Number(process.argv[2] || 2525);
const httpPort = Number(process.argv[3] || 2526);
const outDir = process.argv[4] || path.join(__dirname, 'mails');
fs.mkdirSync(outDir, { recursive: true });
let mails = [];

function decodeBody(raw) {
  // decode quoted-printable / base64 parts loosely so tests can grep the text
  let text = raw;
  const b64 = [...raw.matchAll(/Content-Transfer-Encoding: base64\r?\n(?:[^\r\n]+\r?\n)*\r?\n([A-Za-z0-9+/=\r\n]+)/gi)];
  for (const m of b64) {
    try { text += '\n' + Buffer.from(m[1].replace(/\s/g, ''), 'base64').toString('utf8'); } catch (e) { /* ignore */ }
  }
  if (/quoted-printable/i.test(raw)) {
    const qp = raw.replace(/=\r?\n/g, '').replace(/((?:=[0-9A-F]{2})+)/gi, (s) => {
      try { return Buffer.from(s.split('=').filter(Boolean).map((h) => parseInt(h, 16))).toString('utf8'); } catch (e) { return s; }
    });
    text += '\n' + qp;
  }
  return text;
}

net.createServer((socket) => {
  let buffer = '';
  let inData = false;
  let current = { from: null, to: [], data: '' };
  socket.write('220 qa-sink ESMTP\r\n');
  socket.on('data', (chunk) => {
    buffer += chunk.toString('utf8');
    let idx;
    while (true) {
      if (inData) {
        idx = buffer.indexOf('\r\n.\r\n');
        if (idx < 0) return;
        current.data += buffer.slice(0, idx);
        buffer = buffer.slice(idx + 5);
        inData = false;
        const subject = (current.data.match(/^Subject: (.*)$/mi) || [])[1] || '';
        const mail = { id: mails.length + 1, at: new Date().toISOString(), from: current.from, to: current.to, subject, text: decodeBody(current.data) };
        mails.push(mail);
        fs.writeFileSync(path.join(outDir, `${mail.id}.eml`), current.data);
        current = { from: null, to: [], data: '' };
        socket.write('250 OK queued\r\n');
        continue;
      }
      idx = buffer.indexOf('\r\n');
      if (idx < 0) return;
      const line = buffer.slice(0, idx);
      buffer = buffer.slice(idx + 2);
      const cmd = line.slice(0, 4).toUpperCase();
      if (cmd === 'EHLO' || cmd === 'HELO') socket.write('250-qa-sink\r\n250 8BITMIME\r\n');
      else if (cmd === 'MAIL') { current.from = line.slice(10).trim(); socket.write('250 OK\r\n'); }
      else if (cmd === 'RCPT') { current.to.push(line.slice(8).trim()); socket.write('250 OK\r\n'); }
      else if (cmd === 'DATA') { inData = true; socket.write('354 End data with <CR><LF>.<CR><LF>\r\n'); }
      else if (cmd === 'QUIT') { socket.write('221 Bye\r\n'); socket.end(); return; }
      else socket.write('250 OK\r\n');
    }
  });
  socket.on('error', () => {});
}).listen(smtpPort, '127.0.0.1', () => console.log(`SMTP sink on ${smtpPort}`));

http.createServer((req, res) => {
  if (req.method === 'OPTIONS') { res.writeHead(204, { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Methods': 'GET,DELETE' }); return res.end(); }
  if (req.method === 'DELETE') { mails = []; res.writeHead(204, { 'Access-Control-Allow-Origin': '*' }); return res.end(); }
  res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8', 'Access-Control-Allow-Origin': '*' });
  res.end(JSON.stringify(mails));
}).listen(httpPort, '127.0.0.1', () => console.log(`HTTP mail API on ${httpPort}`));
