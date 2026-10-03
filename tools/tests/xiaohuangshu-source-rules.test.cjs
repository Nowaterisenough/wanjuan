const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { test } = require('node:test');
const vm = require('node:vm');
const { JavaURL, executeRule } = require('./helpers/source-rule-runtime.cjs');

const source = JSON.parse(readFileSync(resolve(__dirname, '../../tests/shareBookSource.json'), 'utf8'))
  .find(item => item.bookSourceName === '小黄书');

function element(attributes = {}, text = '', children = {}) {
  return {
    children,
    attr: name => new String(attributes[name] || ''),
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
function execute(rule, selectors = {}, baseUrl = 'https://reader.example/photo/id-42.html', html = '', ruleSource = source) {
  const context = {
    source: ruleSource, baseUrl, result: html, book: {name: 'Fixture'},
    java: {
      base64Decode: text => Buffer.from(text, 'base64').toString('utf8'),
    },
    Packages: {java: {net: {URL: JavaURL}}},
    org: {jsoup: {Jsoup: {parse: () => ({select: selector => elements(selectors[selector] || [])})}}},
  };
  const { value } = executeRule(rule, context, ruleSource.jsLib, { Packages: context.Packages });
  return {value: JSON.parse(JSON.stringify(value)), book: context.book};
}

function discoveryEntries(ruleSource = source) {
  return JSON.parse(execute(ruleSource.exploreUrl, {}, undefined, '', ruleSource).value);
}

function discoveryUrl(template, page) {
  return template.replace(/\{\{(.*?)\}\}/g, (_, code) => vm.runInNewContext(code, {page}));
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

test('video discovery uses only current categories and sort routes, not retired series IDs', () => {
  const categories = JSON.parse(Buffer.from(source.variableComment, 'base64').toString('utf8'));
  const videos = categories.filmFx.fx;
  assert.equal(videos.length, 22);
  assert.ok(videos.every(item => item.url && !item.id));
  assert.ok(!source.jsLib.includes('/videos/series-'));
  const entries = discoveryEntries().filter(entry => entry.url.includes('/videos/'));
  assert.equal(entries.length, videos.length);
  assert.deepEqual(entries.map(entry => new URL(discoveryUrl(entry.url, 1)).pathname), [
    '/videos/1.html', '/videos/cat-cn.html', '/videos/xs-20.html', '/videos/xs-15.html',
    '/videos/xs-18.html', '/videos/xs-21.html', '/videos/xs-14.html', '/videos/xs-1.html',
    '/videos/xs-8.html', '/videos/xs-27.html', '/videos/xs-30.html', '/videos/xs-3.html',
    '/videos/xs-9.html', '/videos/xs-10.html', '/videos/xs-26.html', '/videos/xs-33.html', '/videos/cat-jav.html',
    '/videos/xs-4.html', '/videos/xs-5.html', '/videos/sort-read.html',
    '/videos/sort-comment.html', '/videos/sort-length.html',
  ]);
  for (const entry of entries) {
    assert.equal(entry.style.layout_flexGrow, 0);
    assert.equal(entry.style.layout_flexBasisPercent, 0.29);
    assert.ok(!entry.url.includes('/series-'));
  }
  for (const heading of discoveryEntries().filter(entry => !entry.url)) {
    assert.equal(heading.style.layout_flexBasisPercent, 1);
  }
});

test('video pagination preserves the category or sort on every subsequent page', () => {
  const entries = discoveryEntries().filter(entry => entry.url.includes('/videos/'));
  for (const entry of entries) {
    const first = new URL(discoveryUrl(entry.url, 1));
    for (const page of [2, 12]) {
      const later = new URL(discoveryUrl(entry.url, page));
      assert.equal(later.origin, first.origin);
      assert.equal(later.pathname, first.pathname === '/videos/1.html'
        ? `/videos/${page}.html` : first.pathname.replace(/\.html$/, `/${page}.html`));
      assert.notEqual(later.pathname, first.pathname);
    }
  }
});

test('discovery accepts explicit URLs while preserving legacy IDs and non-clickable headings', () => {
  const categories = JSON.parse(Buffer.from(source.variableComment, 'base64').toString('utf8'));
  const fixtureCategories = {
    novelFx: {...categories.novelFx, fx: [{title: 'Legacy fiction', id: 'fixture'}]},
    photoFx: {...categories.photoFx, fx: [{title: 'Legacy gallery', id: 'fixture'}]},
    filmFx: {...categories.filmFx, fx: [
      {title: 'Relative', url: 'videos/cat-cn.html', id: 'ignored'},
      {title: 'Absolute', url: 'https://catalog.example/videos/cat-jav.html'},
      {title: 'Heading', id: ''},
      {title: 'Retired ID', id: 'retired'},
    ]},
  };
  const fixtureSource = {...source, variableComment: Buffer.from(JSON.stringify(fixtureCategories)).toString('base64')};
  const entries = discoveryEntries(fixtureSource);
  const byTitle = title => entries.find(entry => entry.title === title);
  assert.equal(new URL(discoveryUrl(byTitle('Legacy fiction').url, 2)).pathname,
    '/fictions/tag-fixture/2.html');
  assert.equal(new URL(discoveryUrl(byTitle('Legacy gallery').url, 2)).pathname,
    '/photos/series-fixture/2.html');
  assert.equal(byTitle('Relative').url, 'https://xchina.co/videos/cat-cn.html');
  assert.equal(byTitle('Absolute').url, 'https://catalog.example/videos/cat-jav.html');
  for (const title of ['Heading', 'Retired ID']) {
    assert.equal(byTitle(title).url, '');
    assert.equal(byTitle(title).style.layout_flexBasisPercent, 1);
  }
});

test('fiction and gallery discovery keep all existing ID routes and pagination', () => {
  const categories = JSON.parse(Buffer.from(source.variableComment, 'base64').toString('utf8'));
  const entries = discoveryEntries();
  for (const [key, route, prefix] of [['novelFx', 'fictions', 'tag'], ['photoFx', 'photos', 'series']]) {
    for (const item of categories[key].fx.filter(item => item.id)) {
      const path = `/${route}/${prefix}-${item.id}`;
      const entry = entries.find(entry => entry.url.split('{{')[0].endsWith(path));
      assert.ok(entry, `${route} discovery ID ${item.id} is missing`);
      assert.equal(new URL(discoveryUrl(entry.url, 1)).pathname, `${path}.html`);
      assert.equal(new URL(discoveryUrl(entry.url, 2)).pathname, `${path}/2.html`);
    }
  }
});

test('discovery and detail covers work with sealed library scope and Java string attributes', () => {
  const selectors = {
    '.image .img, .cover .img, .img, img': [element({style: "background-image:url('/covers/42.webp?sig=a_b')"})],
  };
  const cover = execute(source.ruleExplore.coverUrl, selectors).value;
  assert.ok(cover.startsWith('https://reader.example/covers/42.webp?sig=a_b,{'));
  assert.equal(JSON.parse(cover.slice(cover.indexOf(',{') + 1)).headers.Referer,
    'https://reader.example/photo/id-42.html');
  const detail = execute(source.ruleBookInfo.coverUrl, {
    'meta[property="og:image"]': [element({content: '//cdn.example/detail.jpg'})],
  }).value;
  assert.ok(detail.startsWith('https://cdn.example/detail.jpg,{'));
  const fallback = execute(source.ruleBookInfo.coverUrl, {
    '.photo-image .img, .photo-image img, .fiction-detail .cover img, .video-detail .cover img, video[poster]':
      [element({style: "background-image:url('/page.webp')"})],
  }).value;
  assert.ok(fallback.startsWith('https://reader.example/page.webp,{'));
  assert.equal(execute(source.ruleBookInfo.coverUrl).value, '');
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
