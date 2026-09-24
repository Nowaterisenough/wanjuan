const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { test } = require('node:test');
const vm = require('node:vm');

const samples = JSON.parse(readFileSync(resolve(__dirname, '../../tests/shareBookSource.json'), 'utf8'));
const source = samples.find(item => item.bookSourceName === '绅士漫画');
const content = input => vm.runInNewContext(source.ruleContent.content.slice(4), { result: input });
const urls = html => Array.from(html.matchAll(/<img src="([^"]+)"/g), match => match[1].replaceAll('&amp;', '&'));

test('gallery update retains the imported source identity and uses HTTPS entry points', () => {
  assert.equal(source.bookSourceUrl, 'http://wnacg.com');
  for (const url of [source.searchUrl, source.loginUrl, ...JSON.parse(source.exploreUrl).map(entry => entry.url).filter(Boolean)]) {
    assert.ok(url.startsWith('https://www.wn06.ru/'));
  }
});

test('gallery chapters resolve against the redirected page instead of the legacy domain', () => {
  const script = source.ruleToc.chapterList.split('@js:')[1];
  const result = vm.runInNewContext(script, { result: '<link rel="alternate" href="/feed-index-aid-42.html">' });
  const href = result[0].match(/href="([^"]+)"/)[1];
  assert.equal(new URL(href, 'https://current.example/photos-index-aid-42.html').href,
    'https://current.example/photos-gallery-aid-42.html');
  assert.equal(vm.runInNewContext(script, { result: '' }).length, 0);
});

test('gallery signatures survive escaped document.writeln quotes without a trailing backslash', () => {
  const images = [
    '//cdn.example/data/1.jpg?verify=1789200000-a_b-C&size=full',
    'https://cdn.example/data/2.webp?verify=1789200000-d_e-F',
  ];
  const script = 'var imglist = [' + [...images, images[0]].map(url =>
    '{url:fast_img_host+' + JSON.stringify(url) + ',caption:"[01]"}'
  ).join(',') + ',{url:"/themes/collect.jpg"}];';
  const wrapped = 'document.writeln(' + JSON.stringify(script) + ');';
  for (const input of [script, wrapped, wrapped.replaceAll('/', '\\/')]) {
    assert.deepEqual(urls(content(input)), images.map(url => url.startsWith('//') ? 'https:' + url : url));
  }
});

test('gallery parser excludes unrelated images and rejects an error page', () => {
  const html = '<img src="https://ads.example/banner.jpg">' +
    'var imglist = [{url:"//cdn.example/data/1.png"}];' +
    '<img src="https://ads.example/footer.png">';
  assert.deepEqual(urls(content(html)), ['https://cdn.example/data/1.png']);
  assert.throws(() => content('<html><title>403 Forbidden</title><img src="https://ads.example/banner.jpg"></html>'));
  assert.throws(() => content('var imglist = [];'));
});
