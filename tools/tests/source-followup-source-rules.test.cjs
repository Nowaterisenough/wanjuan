const assert = require('node:assert/strict');
const { readFileSync, writeFileSync, mkdtempSync, rmSync } = require('node:fs');
const { resolve, join, dirname } = require('node:path');
const { tmpdir } = require('node:os');
const { spawnSync } = require('node:child_process');
const { test } = require('node:test');
const { JavaURL, executeRule } = require('./helpers/source-rule-runtime.cjs');

const sources = JSON.parse(readFileSync(resolve(__dirname, '../../tests/shareBookSource.json'), 'utf8'));
const p5 = sources.find(s => s.bookSourceName === 'P5韩漫');
const bird = sources.find(s => s.bookSourceName === '鸟鸟韩漫 NNHanman');
const rouman = sources.find(s => s.bookSourceName === '肉漫屋 Rouman5');
const packages = { java: { net: { URL: JavaURL } } };
const list = nodes => ({ size: () => nodes.length, get: i => nodes[i], first: () => nodes[0] || null });
const image = attrs => ({ attr: key => new String(attrs[key] || '') });
const execute = (rule, additions = {}) => executeRule(rule, {
  result: '', baseUrl: 'https://redirected.example/chapter/42', source: p5,
  Packages: packages, ...additions,
}).value;
const imgSources = html => [...String(html).matchAll(/<img src="([^\n]+?)">/g)].map(m => m[1]);

test('P5 uses the publisher fallback before broken primary URLs without discarding static uploads', () => {
  const nodes = [
    image({'data-original': '/missing.jpg', 'data-fallback': '//cdn.example/static/upload/1.jpg?sig=a&b=2'}),
    image({'data-original': '../images/2.jpg'}),
    image({'src': '/static/upload/book/3.jpg'}),
    image({'src': 'https://pic4.zhimg.com/loading.webp'}),
    image({'src': 'data:image/gif;base64,fixture'}),
    image({'data-fallback': '//cdn.example/static/upload/1.jpg?sig=a&b=2'}),
  ];
  const urls = imgSources(execute(p5.ruleContent.content, {
    org: { jsoup: { Jsoup: { parse: () => ({select: () => list(nodes)}) } } },
  }));
  assert.equal(urls.length, 3);
  assert.ok(urls[0].startsWith('https://cdn.example/static/upload/1.jpg?sig=a&b=2,'));
  assert.ok(urls[1].startsWith('https://redirected.example/images/2.jpg,'));
  assert.ok(urls[2].startsWith('https://redirected.example/static/upload/book/3.jpg,'));
  for (const url of urls) {
    const headers = JSON.parse(url.slice(url.indexOf(',{') + 1)).headers;
    assert.equal(headers.Referer, 'https://redirected.example/chapter/42');
    assert.ok(headers['User-Agent']);
  }
  assert.throws(() => execute(p5.ruleContent.content, {
    org: { jsoup: { Jsoup: { parse: () => ({select: () => list([])}) } } },
  }), /No readable chapter images/);
});

test('bird chapter links follow redirects and standard relative URL semantics', () => {
  const rule = bird.ruleToc.chapterUrl.slice(bird.ruleToc.chapterUrl.indexOf('@js:'));
  const baseUrl = 'https://redirected.example/comic/title.html';
  for (const [result, expected] of [
    ['title/chapter-1.html', 'https://redirected.example/comic/title/chapter-1.html'],
    ['/comic/title/chapter-2.html', 'https://redirected.example/comic/title/chapter-2.html'],
    ['//cdn.example/chapter-3.html', 'https://cdn.example/chapter-3.html'],
    ['', baseUrl],
  ]) assert.equal(execute(rule, {source: bird, result, baseUrl}), expected);
});

test('rouman discovery uses explicit CSS and paginated current routes, never the age-confirmation root', () => {
  const kinds = JSON.parse(rouman.exploreUrl);
  assert.equal(kinds.length, 3);
  assert.equal(kinds[0].url, 'https://rouman5.com/home');
  for (const kind of kinds.slice(1)) {
    assert.ok(kind.url.includes('page={{page}}'));
    assert.notEqual(kind.url.replace('{{page}}', '1'), kind.url.replace('{{page}}', '2'));
  }
  assert.equal(rouman.ruleExplore.bookList, '@css:a.site-comic[href]');
  assert.equal(rouman.ruleExplore.bookUrl, '@css:@href');
  assert.equal(rouman.ruleExplore.name, '@css:h3@text');
  assert.ok(rouman.enabledExplore);
});

test('test runtime rejects invalid Java URL overloads and cannot leak rule source into jsLib', () => {
  assert.throws(() => new JavaURL('https://reader.example/', '/page.jpg'), /URL context/);
  assert.throws(() => executeRule('coverHeaders();', {source: p5},
    'function coverHeaders() { return source.header; }'), /source is not defined/);
});

// Enable JVM checks with a Java executable and a classpath containing Rhino, rhino-tools and Jsoup.
const nativeOptions = {skip: !process.env.SOURCE_RULE_JAVA || !process.env.SOURCE_RULE_RHINO_CLASSPATH};
function native(rule, source, html, baseUrl = 'https://redirected.example/chapter/42', extras = '') {
  const directory = mkdtempSync(join(tmpdir(), 'wanjuan-source-rhino-'));
  const path = join(directory, 'fixture.js');
  const code = rule.replace(/^@js:\s*/, '').replace(/^<js>\s*/, '').replace(/\s*<\/js>$/, '');
  const bindings = 'var source=' + JSON.stringify(source) + ';var result=' + JSON.stringify(html)
    + ';var baseUrl=' + JSON.stringify(baseUrl) + ';var book={};'
    + 'var java={get:function(){return "1";},base64Decode:function(value){return String(new Packages.java.lang.String(Packages.java.util.Base64.getDecoder().decode(String(value)),"UTF-8"));}};'
    + extras;
  const script = `var cx=Packages.org.mozilla.javascript.Context.getCurrentContext();
    cx.setLanguageVersion(Packages.org.mozilla.javascript.Context.VERSION_ES6);cx.setInterpretedMode(true);
    var shared=cx.initStandardObjects();
    cx.evaluateString(shared,${JSON.stringify(source.jsLib || '')},"library",1,null);
    new Packages.org.mozilla.javascript.NativeJavaObject(this,shared,null).preventExtensions();
    var child=new Packages.org.mozilla.javascript.NativeObject();
    var wrapped=new Packages.org.mozilla.javascript.NativeJavaObject(this,child,null);
    wrapped.setPrototype(shared);wrapped.setParentScope(null);
    cx.evaluateString(child,${JSON.stringify(bindings)},"bindings",1,null);
    var value=cx.evaluateString(child,${JSON.stringify(code)},"rule",1,null);
    Packages.org.mozilla.javascript.ScriptableObject.putProperty(child,"fixtureResult",value);
    print(cx.evaluateString(child,"JSON.stringify({value:fixtureResult,book:book})","output",1,null));`;
  writeFileSync(path, script, 'utf8');
  try {
    const result = spawnSync(process.env.SOURCE_RULE_JAVA,
      ['-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8',
        '-cp', process.env.SOURCE_RULE_RHINO_CLASSPATH,
        'org.mozilla.javascript.tools.shell.Main', '-f', path], {encoding: 'utf8', timeout: 15000});
    assert.equal(result.status, 0, result.stderr || result.error?.message);
    return JSON.parse(result.stdout.trim());
  } finally {
    assert.equal(dirname(resolve(directory)), resolve(tmpdir()));
    rmSync(directory, {recursive: true, force: true});
  }
}

test('native Rhino and Jsoup: P5 fallback images and relative detail covers', nativeOptions, () => {
  const value = native(p5.ruleContent.content, p5,
    '<img class="lazy" data-original="/broken.jpg" data-fallback="/static/upload/real.jpg"><img class="lazy" src="/page2.jpg">').value;
  assert.equal(imgSources(value).length, 2);
  assert.ok(value.includes('https://redirected.example/static/upload/real.jpg,'));
  const info = JSON.parse(native(p5.ruleBookInfo.init, p5,
    '<div class="detail-main-cover"><img src="/cover.jpg"></div><p class="detail-main-info-title">Fixture</p>').value);
  assert.equal(info.cover, 'https://redirected.example/cover.jpg');
});

test('native Rhino and Jsoup: bird lazy attributes and URL context are usable', nativeOptions, () => {
  const value = native(bird.ruleContent.content, bird,
    '<div class="img-wrap"><img data-src="//cdn.example/1.jpg"></div><div class="view-imgBox"><img src="../2.jpg"></div>').value;
  assert.equal(imgSources(value).length, 2);
  assert.ok(value.includes('https://redirected.example/2.jpg,'));
});

test('native Rhino and Jsoup: sealed shared library can generate covers and body headers', nativeOptions, () => {
  const source = sources.find(s => s.bookSourceName === '小黄书');
  const value = native(source.ruleContent.content, source,
    '<div class="photo-image"><div class="img" style="background-image:url(\'/1.webp?sig=a_b\')"></div></div>',
    'https://reader.example/photo/id-42.html');
  assert.equal(value.book.type, 64);
  assert.ok(value.value.includes('https://reader.example/1.webp?sig=a_b,{'));
  const cover = native(source.ruleBookInfo.coverUrl, source,
    '<meta property="og:image" content="//cdn.example/cover.jpg">').value;
  assert.ok(cover.startsWith('https://cdn.example/cover.jpg,{'));
  assert.ok(JSON.parse(native(source.exploreUrl, source, '').value).length > 60);
});

test('native Rhino: fiction images and video headers do not need rule bindings in jsLib', nativeOptions, () => {
  const source = sources.find(s => s.bookSourceName === '小黄书');
  const fiction = native(source.ruleContent.content, source,
    '<div class="fiction-body"><p><img src="/page.jpg"></p></div>', 'https://reader.example/fiction/id-42.html');
  assert.equal(fiction.book.type, 64);
  assert.ok(fiction.value.includes('https://reader.example/page.jpg,{'));
  const video = native(source.ruleContent.content, source,
    'new VideoPlayer("player", {src:"/media/master.m3u8?token=fresh"});', 'https://reader.example/video/id-42.html');
  assert.equal(video.book.type, 4);
  assert.ok(video.value.startsWith('https://reader.example/media/master.m3u8?token=fresh,{'));
});

test('native Rhino: rules use Packages rather than the rule helper for Java classes', nativeOptions, () => {
  const comic = sources.find(s => s.bookSourceName === '禁漫');
  assert.ok(native(comic.ruleContent.content, comic, '<center><img data-src="../page.jpg"></center>')
    .value.includes('https://redirected.example/page.jpg'));
  const wan = sources.find(s => s.bookSourceName === '万年漫画');
  assert.ok(native(wan.ruleBookInfo.coverUrl, wan, '<div class="c-img"><img data-src="/cover.jpg"></div>')
    .value.startsWith('https://redirected.example/cover.jpg,'));
  const page = '<article class="excerpt"><h2><a href="/post/42.html">Fixture</a></h2><div class="thumbnail"><img data-src="/cover.jpg"></div></article>';
  const search = native('@js:result=org.jsoup.Jsoup.parse(String(result)).select("article.excerpt").first();'
    + wan.ruleSearch.coverUrl.slice(4), wan, page).value;
  assert.ok(search.startsWith('https://redirected.example/cover.jpg,'));
  const discoveryScript = wan.ruleExplore.bookList.slice(4, wan.ruleExplore.bookList.indexOf('</js>'));
  const discovery = JSON.parse(native(discoveryScript, wan, page).value);
  assert.equal(discovery.data[0].bookUrl, 'https://redirected.example/post/42.html');
});

for (const [name, html] of [
  ['久久漫画', '<div class="view-main-1 readForm"><img src="../1.jpg"></div>'],
  ['久久动漫', '<div class="view-main-1 readForm"><img src="../1.jpg"></div>'],
  ['三四娱乐 San421', '<div class="article-content"><img data-original="../1.jpg"></div>'],
  ['三四娱乐 San449', '<div class="article-content"><img data-original="../1.jpg"></div>'],
  ['漫漫漫画', '<img class="man_img" src="../1.jpg">'],
]) {
  test(`native Rhino and Jsoup: ${name} handles Java empty-string attributes`, nativeOptions, () => {
    const source = sources.find(s => s.bookSourceName === name);
    assert.ok(native(source.ruleContent.content, source, html).value.includes('https://redirected.example/1.jpg,'));
  });
}

test('native Rhino: reader-provided servers, image arrays and signed text URLs use valid constructors', nativeOptions, () => {
  const hitomi = sources.find(s => s.bookSourceName === 'Hitomi');
  const content = hitomi.ruleContent.content.slice(hitomi.ruleContent.content.indexOf('@js:'));
  assert.ok(native(content, hitomi, 'data/1.jpg', undefined,
    'java.getString=function(){return "https://cdn.example/";};').value.includes('https://cdn.example/data/1.jpg,'));
  const comic = sources.find(s => s.bookSourceName === '7mm漫画');
  assert.ok(native(comic.ruleContent.content, comic,
    'Large_cgurl[1] = "https://cdn.example/1.jpg";').value.includes('https://cdn.example/1.jpg,'));
  const novel = sources.find(s => s.bookSourceName === '疯情书库');
  const page = 'function ajaxGetContent(chapid){$.get("./_getcontent.php?id="+chapid+"&v=token")}ajaxGetContent("605")';
  assert.ok(native(novel.ruleContent.content, novel, page, 'https://reader.example/read-605.html',
    'java.ajax=function(url){if(String(url).indexOf("https://reader.example/_getcontent.php?id=605&v=token,")!==0)throw new Error("Wrong URL");return Array(20).join("Readable fixture paragraph. ");};')
    .value.startsWith('Readable fixture paragraph.'));
});
