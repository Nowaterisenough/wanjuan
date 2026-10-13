const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { test } = require('node:test');
const vm = require('node:vm');

const samples = JSON.parse(readFileSync(resolve(__dirname, '../../tests/shareBookSource.json'), 'utf8'));
const source = samples.find(item => item.bookSourceUrl === 'https://jmcomicgo.me');

function context(page, variable = '', credentials = { 账号: 'fixture-user', 密码: 'fixture-password' }) {
  const calls = [];
  const toasts = [];
  return {
    calls, toasts,
    source: {
      getLoginInfo: () => JSON.stringify(credentials),
      getVariable: () => variable,
      setVariable: value => { variable = value; },
    },
    java: {
      ajax: request => {
        const separator = request.indexOf(',');
        const url = separator < 0 ? request : request.slice(0, separator);
        // Match the HTTP client's requirement before accepting a request.
        assert.ok(['http:', 'https:'].includes(new URL(url).protocol));
        calls.push({ url, options: separator < 0 ? null : JSON.parse(request.slice(separator + 1)) });
        return separator < 0 ? page : '<html>fixture login response</html>';
      },
      longToast: value => toasts.push(value),
    },
  };
}

for (const [published, expected] of [
  ['comic.example', 'https://comic.example'],
  [' https://comic.example/// ', 'https://comic.example'],
  ['http://comic.example/', 'http://comic.example'],
  ['//comic.example/', 'https://comic.example'],
]) {
  test(`login and link discovery normalize ${JSON.stringify(published)}`, () => {
    for (const markup of [
      `<div class="china"><span>${published}</span></div>`,
      `<a class='link china' href='#'>\n <span class='domain'>${published}</span></a>`,
    ]) {
      for (const action of ['login()', 'check()']) {
        const scope = context(markup);
        vm.runInNewContext(source.loginUrl + '\n' + action, scope);
        assert.equal(scope.source.getVariable(), expected);
        if (action === 'login()') {
          assert.equal(scope.calls.length, 2);
          assert.equal(scope.calls[1].url, expected + '/login');
          assert.equal(scope.calls[1].options.method, 'POST');
        } else {
          assert.equal(scope.calls.length, 1);
          assert.deepEqual(scope.toasts, [expected]);
        }
      }
    }
  });
}

test('login form preserves special characters in both credentials', () => {
  const credentials = { 账号: '用户 &+?=', 密码: 'fixture&password+with=%# 空格' };
  const scope = context('<div class="china"><span>comic.example</span></div>', '', credentials);
  vm.runInNewContext(source.loginUrl + '\nlogin()', scope);
  const form = new URLSearchParams(scope.calls[1].options.body);
  assert.equal(form.get('username'), credentials.账号);
  assert.equal(form.get('password'), credentials.密码);
  assert.equal(form.get('submit_login'), '1');
  assert.equal([...form].length, 3);
});

test('invalid publishing pages stop before sending credentials or replacing the saved URL', () => {
  for (const page of [
    '<html>publishing page unavailable</html>',
    '<div class="china"><span> </span></div>',
    '<div class="china"><span>ftp://comic.example</span></div>',
    '<div class="china"><span>not a hostname</span></div>',
  ]) {
    for (const action of ['login()', 'check()']) {
      const scope = context(page, 'https://saved.example');
      assert.throws(() => vm.runInNewContext(source.loginUrl + '\n' + action, scope), /发布页/);
      assert.equal(scope.calls.length, 1);
      assert.equal(scope.source.getVariable(), 'https://saved.example');
    }
  }
});

test('discover and search repair previously saved domain-only URLs', () => {
  for (const [saved, expected] of [
    [' comic.example/// ', 'https://comic.example'],
    ['//comic.example/', 'https://comic.example'],
    ['http://comic.example/', 'http://comic.example'],
    ['https://comic.example/', 'https://comic.example'],
    ['', source.bookSourceUrl],
  ]) {
    const scope = context('', saved);
    const entries = JSON.parse(vm.runInNewContext(source.exploreUrl.slice(4).replaceAll('{{page}}', '2'), scope));
    const urls = entries.filter(entry => entry.url).map(entry => entry.url);
    assert.ok(urls.length > 0);
    assert.ok(urls.every(url => url.startsWith(expected + '/')));
    const searchUrl = source.searchUrl.replace(/\{\{(.*?)\}\}/g, (_, expression) => {
      if (expression === 'key') return 'fixture';
      if (expression === 'page') return '2';
      return vm.runInNewContext(expression, scope);
    });
    assert.equal(searchUrl, expected + '/search/photos?search_query=fixture&search-type=photos&main_tag=0&page=2');
  }
});
