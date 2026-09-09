const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { test } = require('node:test');
const vm = require('node:vm');

const samples = JSON.parse(readFileSync(resolve(__dirname, '../../tests/shareBookSource.json'), 'utf8'));
const bundled = JSON.parse(readFileSync(resolve(__dirname, '../../app/src/main/assets/defaultData/bookSources.json'), 'utf8'));

test('comic image rules preserve absolute URLs and resolve relative paths', () => {
  const source = samples.find(item => item.bookSourceName === '国漫吧');
  const urls = ['https://cdn.example/1.jpg', 'http://cdn.example/2.jpg', '//cdn.example/3.jpg', '/Manhua/4.jpg'];
  const script = source.ruleContent.content.replace(/^@js:/, '');
  const html = vm.runInNewContext(script, {
    result: 'eval((function(){}))',
    eval: () => undefined,
    cInfo: { fs: urls },
    baseUrl: 'https://reader.example/chapter/1',
  });
  assert.ok(html.includes('https://cdn.example/1.jpg,'));
  assert.ok(html.includes('http://cdn.example/2.jpg,'));
  assert.ok(html.includes('https://cdn.example/3.jpg,'));
  assert.ok(html.includes('http://images.720rs.com/Manhua/4.jpg,'));
  assert.ok(html.includes('"Referer":"https://reader.example/chapter/1"'));
  assert.ok(!html.includes('720rs.comhttp'));
});

test('desktop comic decoder restores all nonce fragments', () => {
  const source = samples.find(item => item.bookSourceName === '腾讯漫画');
  const data = Buffer.from(JSON.stringify({ picture: [{ url: 'https://cdn.example/page.jpg' }] })).toString('base64');
  const result = "var DATA = '" + 'xyz' + data + "'; window[\"no\"+\"nce\"] = '0xyz';";
  const html = vm.runInNewContext(source.ruleContent.content.slice(4), {
    result, baseUrl: 'https://reader.example/free-chapter',
    java: { base64Decode: value => Buffer.from(value, 'base64').toString('utf8') },
  });
  assert.ok(html.includes('https://cdn.example/page.jpg,'));
  assert.throws(() => vm.runInNewContext(source.ruleContent.content.slice(4), { result: '<html>login required</html>' }));
});

test('comic CDN comes from public page configuration and paid chapters are refused', () => {
  for (const name of ['知音漫客 ZYMK', '知音漫客 ZYMK备用']) {
    const script = samples.find(item => item.bookSourceName === name).ruleContent.content.slice(4);
    const page = `price:0,start_var:1,end_var:2,chapter_image:{high:"Book/$$.webp"},baidutmpArr:'["http://new-cdn.example/comic/Book/1.webp"]'`;
    const html = vm.runInNewContext(script, { result: page, baseUrl: 'https://reader.example/free' });
    assert.ok(html.includes('https://new-cdn.example/comic/Book/1.webp,'));
    assert.ok(html.includes('https://new-cdn.example/comic/Book/2.webp,'));
    assert.equal((html.match(/<img /g) || []).length, 2);
    assert.throws(() => vm.runInNewContext(script, { result: page.replace('price:0', 'price:1') }));
  }
});

test('image header decoding only removes the known wrapper before a JPEG signature', () => {
  const jpeg = Buffer.from([255, 216, 255, 224, 1, 2, 3]);
  const wrapped = Buffer.concat([Buffer.alloc(80, 254), jpeg]);
  for (const name of ['新爱漫画', '新人漫画']) {
    const rule = samples.find(s => s.bookSourceName === name).ruleContent.imageDecode;
    for (const bytes of [jpeg, wrapped]) {
      const decoded = vm.runInNewContext(rule, {
        result: bytes,
        Packages: { java: { util: { Arrays: { copyOfRange: (b, start, end) => b.subarray(start, end) } } } },
      });
      assert.deepEqual(decoded, jpeg);
    }
  }
});

test('public image AES decoding uses the prefixed IV', () => {
  const crypto = require('node:crypto');
  const rule = samples.find(s => s.bookSourceName === '51漫画').ruleContent.imageDecode;
  const key = Buffer.from('NlgrYjYuRT5ic1hifSs9Tg==', 'base64');
  const iv = Buffer.alloc(16, 17);
  const clear = Buffer.from('RIFF fixture WEBP payload');
  const cipher = crypto.createCipheriv('aes-128-cbc', key, iv);
  const encrypted = Buffer.concat([iv, cipher.update(clear), cipher.final()]);
  const decoded = vm.runInNewContext(rule, {
    result: encrypted,
    Packages: { java: { util: { Arrays: { copyOfRange: (b, start, end) => b.subarray(start, end) } } } },
    java: {
      base64DecodeToByteArray: s => Buffer.from(s, 'base64'),
      createSymmetricCrypto: (mode, actualKey, actualIv) => ({ decrypt: bytes => {
        assert.equal(mode, 'AES/CBC/PKCS5Padding');
        const decipher = crypto.createDecipheriv('aes-128-cbc', actualKey, actualIv);
        return Buffer.concat([decipher.update(bytes), decipher.final()]);
      } }),
    },
  });
  assert.deepEqual(decoded, clear);
});

test('mobile chapter rules fetch all public image batches and reject denied responses', () => {
  for (const name of ['野蛮漫画', '漫神 MHKami', '永远漫画', '永远漫画 YYDSMH']) {
    const rule = samples.find(s => s.bookSourceName === name).ruleContent.content.slice(4);
    const calls = [];
    const context = {
      result: '<html>fixture</html>', baseUrl: 'https://reader.example/chapter/1',
      org: { jsoup: { Jsoup: { parse: () => ({ select: selector => selector.includes('img-box')
        ? { size: () => 0 }
        : { size: () => 12, first: () => ({ attr: key => key === 'data-aid' ? '2' : '1' }) } }) } } },
      java: { ajax: request => {
        const options = JSON.parse(request.slice(request.indexOf(',') + 1));
        const offset = Number(new URLSearchParams(options.body).get('offset'));
        calls.push(offset);
        return JSON.stringify({ code: 1, data: { pic: Array.from({ length: Math.min(10, 12 - offset) },
          (_, i) => ({ pic: `https://cdn.example/${offset + i}.jpg` })) } });
      } },
    };
    const html = vm.runInNewContext(rule, context);
    assert.deepEqual(calls, [0, 10]);
    assert.equal((html.match(/<img /g) || []).length, 12);
    context.java.ajax = () => JSON.stringify({ code: 0, msg: 'Login required' });
    assert.throws(() => vm.runInNewContext(rule, context), /Login required/);
  }
});

test('signed chapter endpoints request fresh responses without changing authorization fields', () => {
  for (const name of ['漫客栈', '漫客栈备用']) {
    const url = samples.find(s => s.bookSourceName === name).ruleToc.chapterUrl;
    const options = JSON.parse(url.slice(url.indexOf(',') + 1));
    const endpoint = 'https://reader.example/content?sign=fixture&uid=1';
    for (const now of [1000, 2000]) {
      assert.equal(vm.runInNewContext(options.js, { result: endpoint, Date: { now: () => now } }),
        endpoint + '&_=' + now);
    }
    assert.ok(!url.includes('{{Date.now()}}'));
    assert.ok(url.includes('&sign='));
    assert.ok(url.includes('&uid='));
  }
});

test('comic search avoids HTTP redirects while preserving source identity', () => {
  for (const name of ['看漫画吧', '看漫画 Kanman HTML']) {
    const source = samples.find(s => s.bookSourceName === name);
    assert.ok(source.searchUrl.startsWith('https://m.kanman.com/'));
    assert.ok(source.bookSourceUrl);
  }
});
