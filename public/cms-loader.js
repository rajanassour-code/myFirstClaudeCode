// CMS content loader — fetches JSON from /api/content/:section and provides helpers

function loadPage(section, cb) {
  fetch('/api/content/' + section)
    .then(function(r) { return r.json(); })
    .then(cb)
    .catch(function(e) { console.error('CMS load error:', e); });
}

function loadContact(cb) {
  fetch('/api/content/contact')
    .then(function(r) { return r.json(); })
    .then(cb)
    .catch(function(e) { console.error('CMS contact load error:', e); });
}

function setText(id, value) {
  var el = document.getElementById(id);
  if (el) el.innerHTML = value;
}

function renderFooter(id, c) {
  var el = document.getElementById(id);
  if (!el) return;
  el.innerHTML =
    '<div class="footer-inner">' +
      '<div>' +
        '<div class="footer-brand-badge">RJN</div>' +
        '<div class="footer-name">Raja J. Nassour</div>' +
        '<div class="footer-tagline">Senior Team Leader &amp; Banking Technology Specialist<br>Fanar, Beirut — Lebanon · 19+ years in financial systems</div>' +
        '<div class="footer-contacts">' +
          '<div class="footer-contact-row"><span>📞</span><a href="tel:' + c.phone.replace(/\s/g,'') + '">' + c.phone + '</a></div>' +
          '<div class="footer-contact-row"><span>✉️</span><a href="mailto:' + c.email + '">' + c.email + '</a></div>' +
        '</div>' +
      '</div>' +
      '<div class="footer-nav-col">' +
        '<h4>Navigation</h4>' +
        '<ul class="footer-nav-list">' +
          '<li><a href="index.html">Home</a></li>' +
          '<li><a href="about.html">About</a></li>' +
          '<li><a href="experience.html">Experience</a></li>' +
          '<li><a href="skills.html">Skills</a></li>' +
          '<li><a href="contact.html">Contact</a></li>' +
          '<li><a href="CV - Raja Nassour-5.pdf" download>Download CV</a></li>' +
        '</ul>' +
      '</div>' +
    '</div>' +
    '<div class="footer-bottom">' +
      '<span>&copy; ' + new Date().getFullYear() + ' Raja J. Nassour. All rights reserved.</span>' +
      '<span>Built with care · Beirut, Lebanon</span>' +
    '</div>';
}

// Mobile nav toggle
document.addEventListener('DOMContentLoaded', function() {
  var nt = document.getElementById('nt');
  var nl = document.getElementById('nl');
  if (nt && nl) nt.addEventListener('click', function() { nl.classList.toggle('open'); });
});
