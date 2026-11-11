/* Hamgam landing page. No dependencies, no requests to any other host. */
(function () {
  'use strict';

  var signup = document.getElementById('signup');
  if (signup) {
    // Report the first interaction with the form (once per page load) to our own server.
    var reported = false;
    var report = function () {
      if (reported) return;
      reported = true;
      try {
        var body = new URLSearchParams();
        body.append('v', signup.elements.v ? signup.elements.v.value : '');
        if (navigator.sendBeacon) {
          navigator.sendBeacon('/e/start', body);
        } else {
          fetch('/e/start', { method: 'POST', body: body, keepalive: true, credentials: 'same-origin' });
        }
      } catch (e) { /* tracking must never break the form */ }
    };
    signup.addEventListener('focusin', report);
    signup.addEventListener('input', report);
    signup.addEventListener('change', report);

    // The price question is optional: tapping the selected answer again clears it.
    var selected = signup.querySelector('input[type=radio]:checked');
    Array.prototype.forEach.call(signup.querySelectorAll('input[type=radio]'), function (radio) {
      radio.addEventListener('click', function () {
        if (selected === radio) {
          radio.checked = false;
          selected = null;
        } else {
          selected = radio;
        }
      });
    });
  }

  // Bring a server-side error message into view.
  var error = document.querySelector('.err');
  if (error && error.scrollIntoView) {
    error.scrollIntoView({ block: 'center' });
  }

  // Guard against double taps on slow connections.
  Array.prototype.forEach.call(document.querySelectorAll('form'), function (form) {
    form.addEventListener('submit', function () {
      var button = form.querySelector('button[type=submit]');
      if (button) setTimeout(function () { button.disabled = true; }, 0);
    });
  });
  window.addEventListener('pageshow', function () {
    Array.prototype.forEach.call(document.querySelectorAll('button[disabled]'), function (button) {
      button.disabled = false;
    });
  });
})();
