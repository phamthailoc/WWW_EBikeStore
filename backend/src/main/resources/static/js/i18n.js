// Script text. The shop speaks Vietnamese only: scripts write their Vietnamese text as t('...'), and t returns it as it is,
// filling {name}-style values. t('js.key') reads a message the server put in window.I18N (see I18nModelAdvice).
(function () {
  'use strict';
  var messages = window.I18N || {};

  // t('js.key', {name: 'An'}) fills {name}; a text that is not a key comes back as it is.
  window.t = function (key, values) {
    var text = Object.prototype.hasOwnProperty.call(messages, key) ? messages[key] : key;
    if (values) {
      text = text.replace(/\{(\w+)\}/g, function (match, name) {
        return Object.prototype.hasOwnProperty.call(values, name) ? values[name] : match;
      });
    }
    return text;
  };
  window.tBikes = function (n) {
    return window.t('{n} xe', {n: n});
  };
})();
