const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { test } = require('node:test');
const vm = require('node:vm');

const samples = JSON.parse(readFileSync(resolve(__dirname, '../../tests/shareBookSource.json'), 'utf8'));
const source = samples.find(item => item.bookSourceName === '绅士漫画');
const content = (input, baseUrl = 'https://current.example/photos-gallery-aid-42.html') =>
  vm.runInNewContext(source.ruleContent.content.slice(4), { result: input, baseUrl, source });
const requests = html => Array.from(html.matchAll(/<img[^>]*src="([^"\n]*(?:"[^>]+\})?)"[^>]*>/g), match => {
  const value = match[1].replaceAll('&amp;', '&');
  const split = value.indexOf(',{');
  return {url: value.slice(0, split), options: JSON.parse(value.slice(split + 1))};
});
const urls = html => requests(html).map(request => request.url);

function link(href, title, text = title) {
  return {attr: name => ({href, title})[name] || '', text: () => text};
}
function toc({chapters = [], reader = null, alternate = null} = {}, baseUrl = '') {
  function elements(items) {
    return {size: () => items.length, get: index => items[index], first: () => items[0] || null};
  }
  const doc = {select: selector => {
    if (selector.startsWith('.sr_compact')) return elements(chapters);
    if (selector.startsWith('#reader-btn')) return elements(reader ? [reader] : []);
    if (selector.startsWith('link[rel')) return elements(alternate ? [alternate] : []);
    throw new Error('Unexpected selector: ' + selector);
  }};
  return JSON.parse(JSON.stringify(vm.runInNewContext(source.ruleToc.chapterList.slice(4), {
    result: '', baseUrl, org: {jsoup: {Jsoup: {parse: () => doc}}},
  })));
}

test('gallery update retains the imported source identity and uses HTTPS entry points', () => {
  assert.equal(source.bookSourceUrl, 'http://wnacg.com');
  for (const url of [source.searchUrl, source.loginUrl, ...JSON.parse(source.exploreUrl).map(entry => entry.url).filter(Boolean)]) {
    assert.ok(url.startsWith('https://www.wn06.ru/'));
  }
});

test('gallery chapters resolve against the redirected page instead of the legacy domain', () => {
  const result = toc({alternate: link('/feed-index-aid-42.html', '')});
  assert.equal(new URL(result[0].url, 'https://current.example/photos-index-aid-42.html').href,
    'https://current.example/photos-gallery-aid-42.html');
  assert.equal(toc().length, 0);
  assert.equal(toc({}, 'https://current.example/photos-index-aid-42.html')[0].url,
    '/photos-gallery-aid-42.html');
});

test('collection chapters use child albums and preserve the parent series context', () => {
  const result = toc({chapters: [
    link('/photos-slide-aid-101-sid-42.html', '第1話 Fixture 45P 2026-10-02'),
    link('/photos-slide-aid-102-sid-42.html', '第2話 Fixture 23P'),
  ]}, 'https://current.example/photos-index-aid-42.html');
  assert.deepEqual(result, [
    {title: '第1話 Fixture', url: '/photos-gallery-aid-101-sid-42.html'},
    {title: '第2話 Fixture', url: '/photos-gallery-aid-102-sid-42.html'},
  ]);
  assert.equal(source.ruleToc.chapterName, 'title');
  assert.ok(source.ruleToc.chapterUrl.startsWith('url##'));
});

test('descending thumbnail catalogs sort chapters and discard duplicate cover links', () => {
  const result = toc({chapters: [
    link('/photos-slide-aid-103-sid-42.html', '第3話 Fixture'),
    link('/photos-slide-aid-103-sid-42.html', 'Fixture duplicate'),
    link('/photos-slide-aid-101-sid-42.html', '第1話 Fixture'),
    link('/photos-slide-aid-102-sid-42.html', '第2話 Fixture'),
  ]});
  assert.deepEqual(result.map(chapter => chapter.url), [
    '/photos-gallery-aid-101-sid-42.html', '/photos-gallery-aid-102-sid-42.html',
    '/photos-gallery-aid-103-sid-42.html',
  ]);
});

test('chapter requests refresh signed image manifests without changing stored chapter IDs', () => {
  const options = JSON.parse(source.ruleToc.chapterUrl.split('##$##,')[1]);
  for (const url of ['https://current.example/gallery.html', 'https://current.example/gallery.html?order=asc']) {
    assert.equal(vm.runInNewContext(options.js, {result: url, Date: {now: () => 1000}}),
      url + (url.includes('?') ? '&' : '?') + '_=1000');
  }
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

test('image requests use the current chapter referer rather than the legacy source identity', () => {
  const chapter = 'https://current.example/photos-gallery-aid-101-sid-42.html';
  const result = requests(content('var imglist = [{url:"//cdn.example/data/1.gif?verify=fixture"}];', chapter));
  assert.equal(result[0].url, 'https://cdn.example/data/1.gif?verify=fixture');
  assert.equal(result[0].options.headers.Referer, chapter);
  assert.ok(result[0].options.headers['User-Agent']);
  assert.equal(JSON.parse(source.header).Referer, 'https://www.wn06.ru/');
});
