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
