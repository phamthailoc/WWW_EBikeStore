// Page language (V19). The server renders the page in the chosen language and passes the strings scripts need in
// window.I18N (see I18nModelAdvice); t(key) returns that text. A missing key returns the key's own fallback, so a
// string nobody translated yet shows up as the Vietnamese text instead of an empty gap.
(function () {
  'use strict';
  var messages = window.I18N || {};
  var lang = (document.documentElement.getAttribute('lang') || 'vi').slice(0, 2);

  // t('js.key', {name: 'An'}) fills {name}; unknown keys return the key so a gap is easy to spot.
  window.t = function (key, values) {
    var text = Object.prototype.hasOwnProperty.call(messages, key) ? messages[key] : key;
    if (values) {
      text = text.replace(/\{(\w+)\}/g, function (match, name) {
        return Object.prototype.hasOwnProperty.call(values, name) ? values[name] : match;
      });
    }
    return text;
  };
  window.ebikeLang = {
    get: function () { return lang; },
    // The server stores the choice (cookie, and the account when signed in) and sends us back to the same page.
    set: function (code) {
      location.href = '/lang/' + encodeURIComponent(code) + '?back=' + encodeURIComponent(location.pathname + location.search);
    }
  };

  function mountSwitch() {
    var host = document.querySelector('.top .actions') || document.querySelector('.top');
    if (!host || host.querySelector('.lang-switch')) return;
    var other = lang === 'en' ? 'vi' : 'en';
    var link = document.createElement('a');
    link.className = 'lang-switch';
    link.href = '/lang/' + other + '?back=' + encodeURIComponent(location.pathname + location.search);
    link.textContent = other.toUpperCase();
    link.title = other === 'en' ? 'English' : 'Tiếng Việt';
    link.setAttribute('hreflang', other);
    link.setAttribute('aria-label', link.title);
    host.insertBefore(link, host.querySelector('.theme-toggle'));
  }

  // theme.js builds the .actions group on pages that lack one, so wait for it before placing the switch.
  window.addEventListener('load', mountSwitch);
  if (document.readyState === 'complete') mountSwitch();
})();
