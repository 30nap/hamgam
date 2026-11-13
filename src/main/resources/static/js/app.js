/* Hamgam landing page. No dependencies, no requests to any other host. */
(function () {
  'use strict';

  var each = function (list, fn) { Array.prototype.forEach.call(list, fn); };
  var calm = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

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
    each(signup.querySelectorAll('input[type=radio]'), function (radio) {
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

  // Price demo: every price is dollars x rate x (1 + margin), rounded to 1,000 toman.
  var demo = document.getElementById('demo');
  var slider = demo && demo.querySelector('input[type=range]');
  if (slider) {
    var persian = function (text) {
      return String(text).replace(/\d/g, function (d) { return '۰۱۲۳۴۵۶۷۸۹'.charAt(+d); });
    };
    var grouped = function (n) {
      return persian(String(Math.round(n)).replace(/\B(?=(\d{3})+$)/g, '٬'));
    };
    var yesterday = +demo.getAttribute('data-yesterday');
    var min = +slider.min;
    var max = +slider.max;
    var rateText = demo.querySelector('[data-rate]');
    var deltaText = demo.querySelector('[data-delta]');
    var rows = demo.querySelectorAll('[data-usd]');

    var show = function (rate) {
      rateText.textContent = grouped(rate);
      var percent = Math.round((rate / yesterday - 1) * 1000) / 10;
      deltaText.textContent = persian(String(percent).replace('.', '٫')) + '٪';
      each(rows, function (row) {
        var price = row.getAttribute('data-usd') * rate * (1 + row.getAttribute('data-margin') / 100);
        row.querySelector('[data-now]').textContent = grouped(Math.round(price / 1000) * 1000);
      });
      slider.style.setProperty('--p', (rate - min) / (max - min));
    };

    var frame = 0;
    var release = function () { cancelAnimationFrame(frame); };
    slider.addEventListener('input', function () { release(); show(+slider.value); });
    slider.addEventListener('pointerdown', release);
    slider.hidden = false;
    demo.querySelector('.rate-hint').hidden = false;
    show(+slider.value);

    // The one moment of motion on the page: the first time the demo is on screen, the rate
    // climbs once and the prices follow it. Touching the slider takes over immediately.
    if (!calm && 'IntersectionObserver' in window) {
      var watcher = new IntersectionObserver(function (entries) {
        if (!entries[0].isIntersecting) return;
        watcher.disconnect();
        var from = +slider.value;
        var to = 108500;
        var started = 0;
        var step = function (now) {
          if (!started) started = now;
          var t = Math.min((now - started - 500) / 1500, 1);
          if (t > 0) {
            var eased = 1 - Math.pow(1 - t, 3);
            var rate = Math.round((from + (to - from) * eased) / 100) * 100;
            slider.value = t < 1 ? Math.round(rate / 500) * 500 : to;
            show(t < 1 ? rate : to);
          }
          if (t < 1) frame = requestAnimationFrame(step);
        };
        frame = requestAnimationFrame(step);
      }, { threshold: 0.6 });
      watcher.observe(demo);
    }
  }

  // On a phone, keep the sign-up button within reach while neither the first button
  // nor the form itself is on screen.
  var bar = document.getElementById('bar');
  var cta = document.getElementById('cta');
  var form = document.getElementById('form');
  if (bar && cta && form && 'IntersectionObserver' in window) {
    var seen = { cta: true, form: false };
    var inView = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) { seen[entry.target.id] = entry.isIntersecting; });
      bar.classList.toggle('on', !seen.cta && !seen.form);
    });
    inView.observe(cta);
    inView.observe(form);
  }

  // Thank-you page: pass the link on to another seller.
  var share = document.getElementById('share');
  if (share) {
    var button = share.querySelector('button');
    var status = share.querySelector('[role=status]');
    var link = location.origin + share.getAttribute('data-path');
    var canShare = typeof navigator.share === 'function';
    var canCopy = !!(navigator.clipboard && navigator.clipboard.writeText);
    if (canShare || canCopy) {
      button.textContent = share.getAttribute(canShare ? 'data-share' : 'data-copy');
      share.hidden = false;
      button.addEventListener('click', function () {
        if (canShare) {
          navigator.share({ title: document.title, url: link }).catch(function () { /* cancelled */ });
        } else {
          navigator.clipboard.writeText(link).then(function () {
            status.textContent = share.getAttribute('data-copied');
          }).catch(function () { /* blocked by the browser */ });
        }
      });
    }
  }

  // Bring a server-side error message into view.
  var error = document.querySelector('.err');
  if (error && error.scrollIntoView) {
    error.scrollIntoView({ block: 'center' });
  }

  // Guard against double taps on slow connections.
  each(document.querySelectorAll('form'), function (form) {
    form.addEventListener('submit', function () {
      var button = form.querySelector('button[type=submit]');
      if (button) setTimeout(function () { button.disabled = true; }, 0);
    });
  });
  window.addEventListener('pageshow', function () {
    each(document.querySelectorAll('button[disabled]'), function (button) {
      button.disabled = false;
    });
  });
})();
