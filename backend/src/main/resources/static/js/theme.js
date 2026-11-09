// Light / dark theme. Loaded in <head> of every page so the theme is applied before the first paint.
// Choice: "system" (follow the device), "light" or "dark".
// Signed-in customers: the server renders their saved choice into <html data-theme> and sets data-theme-user="true";
// changes are saved to user_preferences.theme. Visitors: kept in localStorage only.
(function () {
  'use strict';
  var root = document.documentElement;
  var KEY = 'ebike-theme';
  var CHOICES = ['system', 'light', 'dark'];
  var TEXT = {system: 'Giao diện: theo thiết bị', light: 'Giao diện: sáng', dark: 'Giao diện: tối'};
  var ICON = {system: '◐', light: '☀', dark: '☾'};
  var signedIn = root.getAttribute('data-theme-user') === 'true';

  function valid(value) { return CHOICES.indexOf(value) >= 0; }
  function stored() {
    try { return localStorage.getItem(KEY); } catch (ignored) { return null; }
  }
  function remember(value) {
    try { localStorage.setItem(KEY, value); } catch (ignored) { /* private mode: the choice lasts for this page only */ }
  }

  var choice = signedIn ? root.getAttribute('data-theme') : stored();
  if (!valid(choice)) choice = 'system';
  root.setAttribute('data-theme', choice);

  function saveToAccount(value) {
    if (!signedIn) return Promise.resolve();
    var headers = {'Content-Type': 'application/json', Accept: 'application/json'};
    return fetch('/api/v1/auth/profile', {credentials: 'same-origin', headers: headers})
      .then(function (r) { return r.ok ? r.json() : Promise.reject(new Error('profile')); })
      .then(function (profile) {
        return fetch('/api/v1/users/' + profile.userId + '/preferences', {
          method: 'PUT', credentials: 'same-origin', headers: headers, body: JSON.stringify({theme: value})
        });
      })
      .catch(function () { /* the choice still applies on this device */ });
  }

  function set(value) {
    if (!valid(value)) return;
    choice = value;
    root.setAttribute('data-theme', value);
    remember(value);
    saveToAccount(value);
    document.dispatchEvent(new CustomEvent('ebike-theme', {detail: value}));
  }

  window.ebikeTheme = {get: function () { return choice; }, set: set, choices: CHOICES};

  function mountButton() {
    var host = document.querySelector('.top .actions') || document.querySelector('.top');
    if (!host || host.querySelector('.theme-toggle')) return;
    var button = document.createElement('button');
    button.type = 'button';
    button.className = 'theme-toggle';
    function paint() {
      button.textContent = ICON[choice];
      button.title = TEXT[choice] + ' (bấm để đổi)';
      button.setAttribute('aria-label', TEXT[choice]);
    }
    button.addEventListener('click', function () {
      set(CHOICES[(CHOICES.indexOf(choice) + 1) % CHOICES.length]);
    });
    document.addEventListener('ebike-theme', paint);
    paint();
    host.appendChild(button);
  }

  // Pages with an .actions group get the button inside it. On the others (product detail, login, sign-up) the
  // account link is wrapped in one, so the button sits next to it instead of floating in the middle of the bar.
  function mount() {
    if (!document.querySelector('.top .actions')) {
      var login = document.querySelector('.top > .login');
      if (login) {
        var group = document.createElement('div');
        group.className = 'actions';
        login.parentNode.insertBefore(group, login);
        group.appendChild(login);
      }
    }
    mountButton();
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', mount);
  else mount();
})();
