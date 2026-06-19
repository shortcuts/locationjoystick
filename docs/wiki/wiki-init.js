(function () {
  var NAV_ITEMS = [
    { href: 'index.html', label: 'Getting Started' },
    { href: 'map.html', label: 'Map' },
    { href: 'routes.html', label: 'Routes' },
    { href: 'favorites.html', label: 'Favorites' },
    { href: 'share.html', label: 'Share &amp; Deep Links' },
    { href: 'group.html', label: 'Group Sync' },
    { href: 'settings.html', label: 'Settings' },
    { href: 'overlays.html', label: 'Overlays' },
    { href: 'troubleshooting.html', label: 'Troubleshooting' },
    { href: 'changelog.html', label: 'Changelog' },
    { href: 'privacy.html', label: 'Privacy' },
    { href: 'acknowledgements.html', label: 'Acknowledgements' },
  ];

  var EXT_ITEMS = [
    { href: 'https://github.com/shortcuts/locationjoystick/blob/main/CONTRIBUTING.md', label: 'Contributing' },
    { href: 'https://github.com/shortcuts/locationjoystick/issues/new?labels=enhancement', label: 'Request a feature' },
    { href: 'https://github.com/shortcuts/locationjoystick/issues/new?labels=bug', label: 'Report a bug' },
  ];

  var currentFile = location.pathname.split('/').pop() || 'index.html';

  var navHtml = NAV_ITEMS.map(function (item) {
    var cls = item.href === currentFile ? ' class="active"' : '';
    return '<a href="' + item.href + '"' + cls + '>' + item.label + '</a>';
  }).join('');

  navHtml += '<span class="nav-sep"></span>';
  navHtml += EXT_ITEMS.map(function (item) {
    return '<a class="nav-ext" href="' + item.href + '" target="_blank" rel="noopener">' + item.label + '</a>';
  }).join('');

  var aside = '<aside class="sidebar">'
    + '<a class="brand" href="index.html">'
    +   '<img src="icon.png" alt="" width="32" height="32">'
    +   '<span class="brand-name">locationjoystick</span>'
    + '</a>'
    + '<div id="docsearch"></div>'
    + '<button class="nav-toggle-btn" aria-label="Toggle navigation"'
    +   ' onclick="this.closest(\'.sidebar\').querySelector(\'nav\').classList.toggle(\'open\')">'
    +   '<svg width="16" height="16" viewBox="0 0 16 16" fill="none">'
    +     '<path d="M2 4h12M2 8h12M2 12h12" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>'
    +   '</svg>'
    + '</button>'
    + '<nav>' + navHtml + '</nav>'
    + '<div class="sidebar-foot">'
    +   '<a class="gh-star" href="https://github.com/shortcuts/locationjoystick" target="_blank" rel="noopener">'
    +     '<svg width="13" height="13" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">'
    +       '<path d="M8 .25a.75.75 0 0 1 .673.418l1.882 3.815 4.21.612a.75.75 0 0 1 .416 1.279'
    +         'l-3.046 2.97.719 4.192a.751.751 0 0 1-1.088.791L8 12.347l-3.766 1.98a.75.75 0 0 1'
    +         '-1.088-.79l.72-4.194L.872 6.374a.75.75 0 0 1 .416-1.28l4.21-.611L7.327.668A.75.75 0 0 1 8 .25Z"/>'
    +     '</svg>'
    +     'Star'
    +     '<span class="gh-star-sep"></span>'
    +     '<span class="gh-star-count">—</span>'
    +   '</a><span class="sidebar-foot-lic">· MIT License</span>'
    + '</div>'
    + '</aside>';

  document.querySelector('.layout').insertAdjacentHTML('afterbegin', aside);

  fetch('https://api.github.com/repos/shortcuts/locationjoystick')
    .then(function (r) { return r.json(); })
    .then(function (d) {
      var n = d.stargazers_count;
      var t = n >= 1000 ? (n / 1000).toFixed(1) + 'k' : String(n);
      document.querySelectorAll('.gh-star-count').forEach(function (el) { el.textContent = t; });
    })
    .catch(function () {});

  var ds = document.createElement('script');
  ds.src = 'https://cdn.jsdelivr.net/npm/@docsearch/js@4';
  ds.onload = function () {
    docsearch({
      appId: 'QPBQ67WNIG',
      apiKey: '2f78aa72d06b6402b38d1e345021bddc',
      indexName: 'locationjoystick',
      container: '#docsearch',
    });
  };
  document.head.appendChild(ds);

  document.querySelectorAll('main h2[id], main h3[id]').forEach(function (heading) {
    var btn = document.createElement('button');
    btn.className = 'heading-link';
    btn.type = 'button';
    btn.setAttribute('aria-label', 'Copy link to this section');
    btn.innerHTML = '<svg width="13" height="13" viewBox="0 0 16 16" fill="none">'
      + '<path d="M6.5 9.5a3.5 3.5 0 0 0 5 0l2-2a3.5 3.5 0 0 0-5-5l-1 1" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>'
      + '<path d="M9.5 6.5a3.5 3.5 0 0 0-5 0l-2 2a3.5 3.5 0 0 0 5 5l1-1" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>'
      + '</svg>';
    btn.addEventListener('click', function () {
      var url = location.origin + location.pathname + '#' + heading.id;
      navigator.clipboard.writeText(url).catch(function () {});
      btn.classList.add('copied');
      setTimeout(function () { btn.classList.remove('copied'); }, 1200);
    });
    heading.appendChild(btn);
  });
}());
