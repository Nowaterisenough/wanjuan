const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { test } = require('node:test');
const vm = require('node:vm');

const source = JSON.parse(readFileSync(resolve(__dirname, '../../tests/shareBookSource.json'), 'utf8'))
  .find(item => item.bookSourceName === '小黄书');

function element(attributes = {}, text = '', children = {}) {
  return {
    children,
    attr: name => attributes[name] || '',
    text: () => text,
    html: () => attributes.html || '',
    outerHtml: () => attributes.outerHtml || '',
    select: selector => elements(children[selector] || []),
  };
}
function elements(items) {
  return {
    size: () => items.length,
    get: index => items[index],
    first: () => items[0] || null,
    text: () => items.map(item => item.text()).join(' '),
    html: () => items.map(item => item.html()).join('\n'),
    select: selector => elements(items.flatMap(item => item.children ? item.children[selector] || [] : [])),
  };
}
function execute(rule, selectors = {}, baseUrl = 'https://reader.example/photo/id-42.html', html = '') {
  const context = {
    source, baseUrl, result: html, book: {name: 'Fixture'},
    java: {
      base64Decode: text => Buffer.from(text, 'base64').toString('utf8'),
    },
    Packages: {java: {net: {URL: function JavaURL(base, relative) {
        const url = relative === undefined ? String(base) : new URL(String(relative), String(base)).href;
        this.toString = () => url;
      }}}},
    org: {jsoup: {Jsoup: {parse: () => ({select: selector => elements(selectors[selector] || [])})}}},
  };
  const value = vm.runInNewContext(source.jsLib + '\n' + rule.replace(/^@js:\s*/, ''), context);
  return {value: JSON.parse(JSON.stringify(value)), book: context.book};
}

test('source identity stays stable while discovery and login use the active site', () => {
  assert.equal(source.bookSourceUrl, 'https://xchina001.site');
  assert.equal(source.loginUrl, 'https://xchina.co/categories.html');
  const entries = JSON.parse(execute(source.exploreUrl).value).filter(entry => entry.url);
  assert.ok(entries.length > 60);
  for (const entry of entries) assert.ok(entry.url.startsWith('https://xchina.co/'));
  const gallery = entries.find(entry => entry.url.includes('/photos/'));
  for (const page of [1, 2]) {
    const url = gallery.url.replace(/\{\{(.*?)\}\}/g, (_, code) => vm.runInNewContext(code, {page}));
    assert.ok(url.endsWith(page === 1 ? '.html' : '/2.html'));
    if (page === 1) assert.ok(!url.endsWith('/1.html'));
  }
});

test('gallery directory fills omitted pages and normalizes an already paginated URL', () => {
  const result = execute(source.ruleToc.chapterList, {
    '.pager a[href]': [element({href: '/photo/id-42/2.html'}, '2'), element({href: '/photo/id-42/5.html'}, '5')],
  }, 'https://reader.example/photo/id-42/2.html?lang=zh');
  assert.equal(result.book.type, 64);
  assert.deepEqual(result.value.map(chapter => chapter.url), [
    'https://reader.example/photo/id-42.html?lang=zh',
    'https://reader.example/photo/id-42/2.html?lang=zh',
    'https://reader.example/photo/id-42/3.html?lang=zh',
    'https://reader.example/photo/id-42/4.html?lang=zh',
    'https://reader.example/photo/id-42/5.html?lang=zh',
  ]);
});

test('gallery images retain signatures, resolve paths, and carry actual referer headers', () => {
  const images = [
    element({style: "background-image:url('https://cdn.example/1.webp?signature=a_b-C&size=full')"}),
    element({style: 'background-image: url("//cdn.example/2.png?signature=second")'}),
    element({'data-original': '/images/3.jpg?signature=third'}),
    element({style: "background-image:url('https://cdn.example/1.webp?signature=a_b-C&size=full')"}),
  ];
  const result = execute(source.ruleContent.content, {'.photo-image .img, .photo-image img': images});
  assert.equal(result.book.type, 64);
  const urls = Array.from(result.value.matchAll(/<img[^>]*src="([^"\n]*(?:"[^>]+\})?)"[^>]*>/g), match => match[1]);
  assert.equal(urls.length, 3);
  for (const [index, value] of urls.entries()) {
    const split = value.indexOf(',{');
    const expected = [
      'https://cdn.example/1.webp?signature=a_b-C&size=full',
      'https://cdn.example/2.png?signature=second',
      'https://reader.example/images/3.jpg?signature=third',
    ][index];
    assert.equal(value.slice(0, split), expected);
    const options = JSON.parse(value.slice(split + 1));
    assert.equal(options.headers.Referer, 'https://reader.example/photo/id-42.html');
    assert.ok(options.headers['User-Agent']);
    assert.equal(options.headers.Origin, 'https://reader.example');
    assert.equal(options.headers['Sec-Fetch-Dest'], 'image');
    assert.equal(options.headers['Sec-Fetch-Site'], index === 2 ? 'same-origin' : 'cross-site');
  }
});

test('empty gallery content fails explicitly instead of returning a cover or empty page', () => {
  assert.throws(() => execute(source.ruleContent.content), /图集没有返回正文图片/);
});

test('single-page fiction receives a text chapter without relying on discovery state', () => {
  const result = execute(source.ruleToc.chapterList, {}, 'https://reader.example/fiction/id-42.html');
  assert.equal(result.book.type, 8);
  assert.equal(result.value.length, 1);
  assert.equal(result.value[0].url, 'https://reader.example/fiction/id-42.html');
});

test('image fiction uses its own content structure and preserves embedded request options', () => {
  const imageTag = '<img src="/page.png?signature=fixture">';
  const image = element({src: '/page.png?signature=fixture', outerHtml: imageTag});
  const body = element({html: '<p>' + imageTag + '</p>'}, '', {img: [image]});
  const selectors = {'.fiction-body': [body]};
  const toc = execute(source.ruleToc.chapterList, selectors, 'https://reader.example/fiction/id-42.html');
  assert.equal(toc.book.type, 64);
  const content = execute(source.ruleContent.content, selectors, 'https://reader.example/fiction/id-42.html');
  assert.equal(content.book.type, 64);
  assert.ok(content.value.includes('https://reader.example/page.png?signature=fixture,{"headers":'));
  assert.ok(!content.value.includes('&quot;'));
});

test('relative player configuration returns the media URL and never invokes a browser', () => {
  const result = execute(source.ruleContent.content, {}, 'https://reader.example/video/id-42.html',
    'new VideoPlayer("video-player", {src: "/hls/42/master.m3u8?token=fixture", poster: "/cover.jpg"});');
  assert.equal(result.book.type, 4);
  assert.ok(result.value.startsWith('https://reader.example/hls/42/master.m3u8?token=fixture,{'));
  assert.equal(JSON.parse(result.value.slice(result.value.indexOf(',{') + 1)).headers.Referer,
    'https://reader.example/video/id-42.html');
  assert.ok(!source.ruleContent.content.includes('startBrowser'));
});

test('video error pages cannot pass by returning a cover image', () => {
  assert.throws(() => execute(source.ruleContent.content, {}, 'https://reader.example/video/id-42.html',
    '<div class="video-detail">Verification needed</div>'), /视频页面没有返回播放地址/);
});
