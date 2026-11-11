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

  // Small screens: keep the sign-up button within reach between the first button and the form.
  var dock = document.getElementById('dock');
  var cta = document.getElementById('cta');
  if (dock && cta && signup && 'IntersectionObserver' in window) {
    var ctaVisible = true;
    var formVisible = false;
    var updateDock = function () {
      dock.classList.toggle('on', !ctaVisible && !formVisible);
    };
    new IntersectionObserver(function (entries) {
      ctaVisible = entries[entries.length - 1].isIntersecting;
      updateDock();
    }).observe(cta);
    new IntersectionObserver(function (entries) {
      formVisible = entries[entries.length - 1].isIntersecting;
      updateDock();
    }).observe(signup);
  }

  // Bring a server-side error message into view.
  var error = document.querySelector('.err');
  if (error && error.scrollIntoView) {
    error.scrollIntoView({ block: 'center' });
  }

  // The SMS code: send the form as soon as all six digits are in (typed, pasted or autofilled).
  var code = document.getElementById('code');
  var codeSent = false;
  if (code && code.form) {
    code.addEventListener('input', function () {
      var digits = code.value.replace(/[^0-9۰-۹٠-٩]/g, '');
      if (digits.length !== 6 || codeSent) return;
      codeSent = true;
      if (code.form.requestSubmit) {
        code.form.requestSubmit();
      } else {
        code.form.submit();
      }
    });
  }

  // Guard against double taps on slow connections, and show that something is happening.
  Array.prototype.forEach.call(document.querySelectorAll('form'), function (form) {
    form.addEventListener('submit', function () {
      var button = form.querySelector('button[type=submit]');
      if (button) {
        setTimeout(function () {
          button.disabled = true;
          button.classList.add('busy');
        }, 0);
      }
    });
  });
  window.addEventListener('pageshow', function () {
    codeSent = false;
    Array.prototype.forEach.call(document.querySelectorAll('button[disabled]'), function (button) {
      button.disabled = false;
      button.classList.remove('busy');
    });
  });
})();
