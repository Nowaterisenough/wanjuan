const vm = require('node:vm');

function JavaURL(base, relative) {
  if (relative !== undefined && !(base instanceof JavaURL)) {
    throw new TypeError('java.net.URL requires a URL context, not (String, String)');
  }
  this.value = relative === undefined
    ? new URL(String(base)).href : new URL(String(relative), String(base)).href;
  this.toString = () => this.value;
}

function executeRule(rule, bindings, library = '', globals = {}) {
  // jsLib functions capture their own parent scope, not the caller's rule bindings.
  const shared = vm.createContext({ ...globals });
  if (library) vm.runInContext(library, shared);
  Object.preventExtensions(shared);
  const child = vm.createContext(Object.assign(Object.create(shared), bindings));
  const code = rule.replace(/^@js:\s*/, '').replace(/^<js>\s*/, '').replace(/\s*<\/js>$/, '');
  return { value: vm.runInContext(code, child), context: child };
}

module.exports = { JavaURL, executeRule };
