// A returnTo address comes from the query string, so anyone can write one into a link. It is followed only when it is a path of this site.
// The check parses the address the way the browser will: a tab, a line break or a backslash inside "/\t/evil.com" would otherwise turn it into //evil.com.
(function () {
  'use strict';
  window.safeReturnTo = function (value) {
    if (typeof value !== 'string' || value.charAt(0) !== '/' || /[\u0000- \u007f\\]/.test(value)) return null;
    try {
      var url = new URL(value, location.origin);
      return url.origin === location.origin ? url.pathname + url.search + url.hash : null;
    } catch (ignored) {
      return null;
    }
  };
})();
