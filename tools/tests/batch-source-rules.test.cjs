const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { test } = require('node:test');
const vm = require('node:vm');
const { JavaURL } = require('./helpers/source-rule-runtime.cjs');

const sources = JSON.parse(readFileSync(resolve(__dirname, '../../tests/shareBookSource.json'), 'utf8'));
const source = name => sources.find(item => item.bookSourceName === name);
const packages = { java: { net: { URL: JavaURL }, util: { Arrays: {
  copyOfRange: (bytes, start, end) => Buffer.from(bytes).subarray(start, end),
} } } };
const list = nodes => ({ size: () => nodes.length, get: i => nodes[i], first: () => nodes[0] });
const element = (attrs, text = '', base = 'https://reader.example/chapter') => ({
  attr: key => new String(key.startsWith('abs:') ? new URL(attrs[key.slice(4)] || '', base).href : attrs[key] || ''),
  text: () => text,
});
function execute(script, additions = {}) {
  return vm.runInNewContext(script.replace(/^@js:\s*/, ''), {
    result: '', baseUrl: 'https://reader.example/chapter', Packages: packages, ...additions,
  });
}
const images = html => [...html.matchAll(/<img[^>]*src="([^"\n]*(?:"[^>]+\})?)"[^>]*>/g)].map(match => match[1]);

test('desktop comic nonce expressions have browser Array and document children probes', () => {
  const encoded = Buffer.from(JSON.stringify({ picture: [{ url: 'https://cdn.example/page.jpg' }] })).toString('base64');
  const packed = encoded.slice(0, 1) + 'ab' + encoded.slice(1);
  const page = `var DATA = '${packed}'; window["no" + "nce"] = '0wrong';
    window["no" + "nce"] = (+eval("1 * (!window.Array) + 1")).toString() + "ab";`;
  const html = execute(source('腾讯漫画').ruleContent.content, {
    result: page, java: { base64Decode: value => Buffer.from(value, 'base64').toString('utf8') },
  });
  assert.equal(images(html).length, 1);
  assert.ok(html.includes('https://cdn.example/page.jpg,'));
  const withChildren = `var DATA = 'ab${encoded}'; window["no" + "nce"] = (+eval("1 - !!document.children")).toString() + "ab";`;
  assert.equal(images(execute(source('腾讯漫画').ruleContent.content, {
    result: withChildren, java: { base64Decode: value => Buffer.from(value, 'base64').toString('utf8') },
  })).length, 1);
});

test('dynamic comic source preserves its explicit enable gate', () => {
  assert.throws(() => execute(source('久久漫画').ruleContent.content, {
    java: { get: () => '0' },
  }), /Source is disabled/);
});

test('reader-advertised image servers retain the actual chapter referer', () => {
  const content = source('Hitomi').ruleContent.content;
  const urls = images(execute(content.slice(content.indexOf('@js:')), {
    result: 'data/1.webp?sig=a&b=2\ndata/1.webp?sig=a&b=2\ndata/2.jpg',
    java: { getString: () => 'https://cdn.example/' },
  }));
  assert.equal(urls.length, 2);
  assert.ok(urls[0].startsWith('https://cdn.example/data/1.webp?sig=a&b=2,'));
  assert.equal(JSON.parse(urls[0].slice(urls[0].indexOf(',') + 1)).headers.Referer, 'https://reader.example/chapter');
});

test('paid chapter banners are not treated as comic pages', () => {
  const script = source('漫漫漫画').ruleContent.content;
  const run = nodes => execute(script, {
    org: { jsoup: { Jsoup: { parse: () => ({ select: () => list(nodes) }) } } },
  });
  const banner = element({ src: '/images/pay_end.jpg', 'data-original': '/images/pay_end.jpg' });
  assert.throws(() => run([banner]), /No readable chapter images/);
  const html = run([element({ src: 'https://cdn.example/1.jpg' }), banner]);
  assert.equal(images(html).length, 1);
  assert.ok(!html.includes('pay_end.jpg'));
});

for (const name of ['鸟鸟韩漫 NNHanman', '久久漫画', '久久动漫', '三四娱乐 San421', '三四娱乐 San449', '漫漫漫画']) {
  test(`${name}: lazy images prefer real attributes and preserve request context`, () => {
    const nodes = [element({ 'data-src': '/1.jpg', src: '/loading.jpg' }),
      element({ 'data-original': '//cdn.example/2.webp?a=1&b=2' }),
      element({ 'data-src': '/1.jpg' }), element({ src: '/placeholder.gif' })];
    const html = execute(source(name).ruleContent.content, {
      java: { get: () => '1' },
      org: { jsoup: { Jsoup: { parse: () => ({ select: () => list(nodes) }) } } },
    });
    const urls = images(html);
    assert.equal(urls.length, 2);
    assert.ok(urls[0].startsWith('https://reader.example/1.jpg,'));
    assert.ok(urls[1].startsWith('https://cdn.example/2.webp?a=1&b=2,'));
    assert.equal(JSON.parse(urls[0].slice(urls[0].indexOf(',') + 1)).headers.Referer,
      'https://reader.example/chapter');
    assert.throws(() => execute(source(name).ruleContent.content, {
      java: { get: () => '1' },
      org: { jsoup: { Jsoup: { parse: () => ({ select: () => list([]) }) } } },
    }), /No readable chapter images/);
  });
}

test('streamed gallery manifests do not require eval and discard duplicate image URLs', () => {
  const script = source('肉漫屋 Rouman5').ruleContent.content;
  const page = 'imagePaths:$R[25]=["https://cdn.example/1.jpg?sig=a&b=2","https://cdn.example/1.jpg?sig=a&b=2","https://cdn.example/2.webp"]';
  const urls = images(execute(script, { result: page }));
  assert.equal(urls.length, 2);
  assert.ok(urls[0].includes('?sig=a&b=2,'));
  assert.throws(() => execute(script, { result: '<img src="https://cdn.example/cover.jpg">' }), /No chapter image/);
});

test('encrypted public comic images use the publisher key and the prefixed IV', () => {
  const script = source('漫蛙 Manwaku').ruleContent.imageDecode;
  const key = Buffer.from('0B6666A0-BB59-1381-B746-a0E4C9AC');
  const iv = Buffer.alloc(16, 41);
  const clear = Buffer.from([255, 216, 255, 224, ...Buffer.from('image fixture')]);
  const cipher = crypto.createCipheriv('aes-256-cbc', key, iv);
  const data = Buffer.concat([iv, cipher.update(clear), cipher.final()]);
  const context = { src: 'https://cdn.example/en_images/1.jpg', result: data,
    java: { createSymmetricCrypto: (mode, actualKey, actualIv) => ({ decrypt: bytes => {
      assert.equal(mode, 'AES/CBC/PKCS5Padding');
      const decipher = crypto.createDecipheriv('aes-256-cbc', Buffer.from(actualKey), actualIv);
      return Buffer.concat([decipher.update(bytes), decipher.final()]);
    } }) } };
  assert.deepEqual(execute(script, context), clear);
  assert.deepEqual(execute(script, { ...context, result: clear }), clear);
  assert.throws(() => execute(script, { ...context, result: Buffer.alloc(19) }), /Invalid encrypted image/);
});

test('novel chapters derive their signed public request from the fresh reader page', () => {
  const script = source('疯情书库').ruleContent.content;
  const requests = [];
  const page = 'function ajaxGetContent(chapid){$.get("./_getcontent.php?id="+chapid+"&v=fresh%2Btoken")}ajaxGetContent("605")';
  const context = { result: page, baseUrl: 'https://aabook.xyz/read-605.html',
    org: { jsoup: { Jsoup: { parse: () => ({ select: () => ({ text: () => 'Loading' }) }) } } },
    java: { ajax: request => { requests.push(request); return 'Readable fixture paragraph. '.repeat(12); } } };
  assert.ok(execute(script, context).startsWith('Readable fixture'));
  assert.equal(requests.length, 1);
  assert.ok(requests[0].startsWith('https://aabook.xyz/_getcontent.php?id=605&v=fresh%2Btoken,'));
  context.java.ajax = () => '<html><title>Challenge</title></html>';
  assert.throws(() => execute(script, context), /Chapter content unavailable/);
});

test('novel chapter links switch to the functional desktop reader without losing the chapter id', () => {
  const rule = source('疯情书库').ruleToc.chapterUrl;
  assert.equal(execute(rule.slice(rule.indexOf('@js:')), {
    result: 'read-605.html', baseUrl: 'https://aabook.xyz/m/chapter.php?id=15',
  }), 'https://aabook.xyz/read-605.html');
  assert.ok(source('疯情书库').ruleBookInfo.tocUrl.includes('&p=1&ob=asc'));
});

test('text article sources receive a stable single chapter', () => {
  const chapters = execute(source('CA情色小说').ruleToc.chapterList);
  assert.equal(chapters.length, 1);
  assert.equal(chapters[0].url, 'https://reader.example/chapter');
  assert.ok(source('CA情色小说').ruleContent.content.includes('entry-inner'));
});

test('novel directories exclude ellipsis links, other books, and duplicate chapters', () => {
  const base = 'https://m.po18wx.com/novel/list/51030/1.html';
  const nodes = [element({ href: '/novel/51030/11.html', title: 'Chapter 1' }, '1', base),
    element({ href: '/novel/list/51030/2.html' }, '...', base),
    element({ href: '/novel/51031/21.html' }, 'Other book', base),
    element({ href: '/novel/51030/11.html' }, 'Duplicate', base)];
  const chapters = execute(source('PO18脸红').ruleToc.chapterList, {
    baseUrl: base, org: { jsoup: { Jsoup: { parse: () => ({ select: () => list(nodes) }) } } },
  });
  assert.equal(chapters.length, 1);
  assert.equal(chapters[0].title, 'Chapter 1');
});

test('comic API pagination explicitly stops on terminal and empty responses', () => {
  const script = source('漫漫漫画').ruleToc.nextTocUrl;
  const state = { page: 1, id: 123 };
  const java = { get: key => state[key], put: (key, value) => { state[key] = value; } };
  const url = execute(script, { result: '{"code":1,"data":[{"id":1}]}', java });
  assert.ok(url.includes('id=123&sort=1&page=2'));
  assert.equal(execute(script, { result: '{"code":0,"data":[]}', java }), '');
  assert.equal(execute(script, { result: '{"code":1,"data":[]}', java }), '');
});

test('search paging requests disjoint inclusive offsets', () => {
  const rule = source('QQ阅读').searchUrl;
  const request = page => rule.replace(/\{\{(.*?)\}\}/g, (_, code) => code === 'key' ? 'fixture'
    : vm.runInNewContext(code, { page }));
  assert.ok(request(1).includes('start=0&end=19'));
  assert.ok(request(2).includes('start=20&end=39'));
});

test('legacy comic service identities use the current certified service for requests', () => {
  for (const name of ['爱优漫吧 IYouMan', '爱优漫吧 IYouMan备用']) {
    const item = source(name);
    assert.ok(item.bookSourceUrl.includes('iyouman.com'));
    assert.ok(item.searchUrl.startsWith('https://m.kanman.com/'));
    assert.ok(item.ruleBookInfo.tocUrl.startsWith('https://m.kanman.com/'));
    assert.ok(item.ruleToc.chapterUrl.startsWith('https://m.kanman.com/'));
  }
});

test('JSON APIs use a literal book URI and a valid image selector', () => {
  assert.ok(source('SFACG漫画').ruleSearch.bookUrl.startsWith('https://mm.sfacg.com/b/'));
  assert.ok(source('漫客栈备用').ruleContent.content.includes('$.data.page[*].image'));
  assert.equal(JSON.parse(source('Hitomi').header)['X-Requested-With'], 'XMLHttpRequest');
  assert.ok(source('Hitomi').ruleBookInfo.tocUrl.includes('/mangazine/mi'));
});

test('comic arrays are extracted without joining unrelated URLs into a greedy match', () => {
  const script = source('7mm漫画').ruleContent.content;
  const html = execute(script, { result: 'Large_cgurl[1] = "https://cdn.example/1.jpg";\nLarge_cgurl[2] = "https://cdn.example/2.gif";\nvar ad="https://ad.example/banner.jpg";' });
  assert.equal(images(html).length, 2);
  assert.ok(!html.includes('ad.example'));
});

test('packed media supports base 62 tokens and excludes preview streams', () => {
  const script = source('MissAV').ruleContent.content;
  const dictionary = Array(63).fill('');
  dictionary[0] = 'source'; dictionary[1] = 'video'; dictionary[2] = 'master';
  dictionary[3] = 'preview'; dictionary[62] = 'unused';
  const page = "eval(function(p,a,c,k,e,d){return p;}('0=\\\"https://media.example/1/2.m3u8\\\";3=\\\"https://media.example/trailer.mp4\\\";10=1;',62,63,'" + dictionary.join('|') + "'.split('|'),0,{}))";
  const media = execute(script, { result: page, source: { getKey: () => 'https://missav.ws/' } });
  assert.ok(media.startsWith('https://media.example/video/master.m3u8,'));
  assert.ok(!media.includes('trailer.mp4'));
  assert.ok(!execute(script, { result: page.replace(',62,63,', ',63,63,'),
    source: { getKey: () => 'https://missav.ws/' } }).startsWith('https://media.example/video/master.m3u8'));
});
