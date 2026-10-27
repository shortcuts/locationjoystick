(function () {
  var NAV_ITEMS = [
    { group: 'Start', items: [
      { href: 'index.html', label: 'Getting Started' },
    ] },
    { group: 'Guides', items: [
      { href: 'tasks.html', label: 'Common tasks' },
      { href: 'teleport-to-a-place.html', label: 'Teleport to a place' },
      { href: 'loop-a-route.html', label: 'Loop a route' },
      { href: 'follow-a-friend.html', label: 'Follow a friend' },
      { href: 'move-to-a-new-phone.html', label: 'Move to a new phone' },
      { href: 'tap-to-walk-in-a-game.html', label: 'Tap to walk in a game' },
      { href: 'collect-links-into-a-route.html', label: 'Collect links into a route' },
    ] },
    { group: 'Features', items: [
      { href: 'map.html', label: 'Map' },
      { href: 'overlays.html', label: 'Overlays' },
      { href: 'reconnect-accessory.html', label: 'Reconnect accessory' },
      { href: 'routes.html', label: 'Routes' },
      { href: 'favorites.html', label: 'Favorites' },
      { href: 'location-links.html', label: 'Location Links' },
      { href: 'capture.html', label: 'Capture' },
      { href: 'group.html', label: 'Group Sync' },
    ] },
    { group: 'Configure', items: [
      { href: 'settings.html', label: 'Settings' },
      { href: 'game-setup.html', label: 'Game setup' },
      { href: 'backup-transfer.html', label: 'Backup &amp; Transfer' },
    ] },
    { group: 'Help', items: [
      { href: 'troubleshooting.html', label: 'Troubleshooting' },
      { href: 'control-api.html', label: 'Control API' },
    ] },
    { group: 'About', items: [
      { href: 'changelog.html', label: 'Changelog' },
      { href: 'privacy.html', label: 'Privacy' },
      { href: 'acknowledgements.html', label: 'Acknowledgements' },
    ] },
  ];

  var GROUP_ICONS = {'Start': '<path d=\'M3 8.5 8 3l5 5.5M4.5 7.5V13h7V7.5\'/>',
    'Guides': '<path d=\'M3 3.5h4a1 1 0 0 1 1 1V13a1 1 0 0 0-1-1H3zM13 3.5H9a1 1 0 0 0-1 1V13a1 1 0 0 1 1-1h4z\'/>',
    'Features': '<path d=\'M8 2l1.8 3.7 4 .6-2.9 2.8.7 4L8 11.2 4.4 13.1l.7-4L2.200 6.300l4-.6z\'/>',
    'Configure': '<path d=\'M3 5h10M3 11h10\'/><circle cx=\'6\' cy=\'5\' r=\'1.5\'/><circle cx=\'10\' cy=\'11\' r=\'1.5\'/>',
    'Help': '<circle cx=\'8\' cy=\'8\' r=\'5.5\'/><path d=\'M6.300 6.300a1.800 1.800 0 1 1 2.500 1.700c-.5.300-.8.700-.8 1.200M8 11.300v.2\'/>',
    'About': '<circle cx=\'8\' cy=\'8\' r=\'5.5\'/><path d=\'M8 7.500v3.500M8 5.200v.1\'/>',
    'Community': '<circle cx=\'6\' cy=\'6\' r=\'2\'/><path d=\'M2.500 12.500c0-2 1.600-3.200 3.500-3.200s3.500 1.200 3.500 3.200M10.500 4.300a2 2 0 0 1 0 3.400M11.500 9.500c1.200.4 2 1.400 2 3\'/>'};
  var svg = function (body) {
    return '<svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + body + '</svg>';
  };
  var EXT_ICON = svg('<path d="M6.500 3.500h-3v9h9v-3M9 3h4v4M13 3 7.500 8.500"/>');

  var EXT_ITEMS = [
    { href: 'https://github.com/shortcuts/locationjoystick/blob/main/CONTRIBUTING.md', label: 'Contributing' },
    { href: 'https://github.com/shortcuts/locationjoystick/issues/new?labels=enhancement', label: 'Request a feature' },
    { href: 'https://github.com/shortcuts/locationjoystick/issues/new?labels=bug', label: 'Report a bug' },
    { href: 'https://discord.gg/r9DRnkwbMf', label: 'Discord' },
  ];

  var currentFile = location.pathname.split('/').pop() || 'index.html';

  var navHtml = NAV_ITEMS.map(function (g) {
    var gid = 'nav-group-' + g.group.toLowerCase();
    return '<div role="group" aria-labelledby="' + gid + '"><span class="nav-group" id="' + gid + '">' + svg(GROUP_ICONS[g.group]) + g.group + '</span>' + g.items.map(function (item) {
      var cls = item.href === currentFile ? ' class="active" aria-current="page"' : '';
      return '<a href="' + item.href + '"' + cls + '>' + item.label + '</a>';
    }).join('') + '</div>';
  }).join('');

  navHtml += '<span class="nav-sep"></span>';
  navHtml += '<div role="group" aria-labelledby="nav-group-community"><span class="nav-group" id="nav-group-community">' + svg(GROUP_ICONS.Community) + 'Community</span>'
    + EXT_ITEMS.map(function (item) {
      return '<a class="nav-ext" href="' + item.href + '" target="_blank" rel="noopener">' + item.label + EXT_ICON + '</a>';
    }).join('') + '</div>';

  var aside = '<aside class="sidebar">'
    + '<a class="brand" href="index.html">'
    +   '<img src="icon.png" alt="" width="32" height="32">'
    +   '<span class="brand-name">locationjoystick</span>'
    + '</a>'
    + '<div id="docsearch"></div>'
    + '<button class="nav-toggle-btn" aria-label="Toggle navigation" aria-expanded="false" aria-controls="site-nav">'
    +   '<svg width="16" height="16" viewBox="0 0 16 16" fill="none">'
    +     '<path d="M2 4h12M2 8h12M2 12h12" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>'
    +   '</svg>'
    + '</button>'
    + '<nav id="site-nav" aria-label="Site">' + navHtml + '</nav>'
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

  var mainEl = document.querySelector('main');
  if (!mainEl.id) mainEl.id = 'main-content';
  document.body.insertAdjacentHTML('afterbegin', '<a class="skip-link" href="#' + mainEl.id + '">Skip to content</a>'
    + '<div class="sr-only" id="live-status" role="status"></div>');

  var toggle = document.querySelector('.nav-toggle-btn');
  toggle.addEventListener('click', function () {
    toggle.setAttribute('aria-expanded', String(document.getElementById('site-nav').classList.toggle('open')));
  });

  // Unauthenticated GitHub API allows 60 requests/hour per IP; cache the count for the tab session.
  var showStars = function (t) {
    document.querySelectorAll('.gh-star-count').forEach(function (el) { el.textContent = t; });
  };
  var cachedStars = null;
  try { cachedStars = sessionStorage.getItem('lj-stars'); } catch (e) {}
  if (cachedStars) {
    showStars(cachedStars);
  } else {
    fetch('https://api.github.com/repos/shortcuts/locationjoystick')
      .then(function (r) { return r.json(); })
      .then(function (d) {
        var n = d.stargazers_count;
        var t = n >= 1000 ? (n / 1000).toFixed(1) + 'k' : String(n);
        showStars(t);
        try { sessionStorage.setItem('lj-stars', t); } catch (e) {}
      })
      .catch(function () {});
  }

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

  var pageLink = document.querySelector('nav a.active');
  var outlineHeads = document.querySelectorAll('main h2[id]:not(.item), main h3[id]:not(.item)');
  if (pageLink && outlineHeads.length) {
    var outline = document.createElement('div');
    outline.className = 'nav-outline';
    var outlineLinks = [];
    var outlineParent = [];
    var parentIdx = -1;
    outlineHeads.forEach(function (h, i) {
      if (h.tagName === 'H2') parentIdx = i;
      outlineParent.push(parentIdx);
      var a = document.createElement('a');
      a.className = 'nav-' + h.tagName.toLowerCase();
      a.href = '#' + h.id;
      a.textContent = h.textContent.trim();
      outline.appendChild(a);
      outlineLinks.push(a);
    });
    pageLink.after(outline);

    var activeLink = null;
    // Near page end the target heading cannot reach the top, so later headings also pass the line; pin the hash target until the user scrolls.
    var pinned = -1;
    var pinHash = function () {
      var id = decodeURIComponent(location.hash.slice(1));
      pinned = Array.prototype.findIndex.call(outlineHeads, function (h) { return h.id === id; });
    };
    var spy = function () {
      var idx = 0;
      outlineHeads.forEach(function (h, i) {
        if (h.getBoundingClientRect().top <= window.innerHeight * 0.3) idx = i;
      });
      if (pinned >= 0) idx = pinned;
      var next = outlineLinks[idx];
      if (next === activeLink) return;
      if (activeLink) {
        activeLink.classList.remove('active');
        activeLink.removeAttribute('aria-current');
      }
      next.classList.add('active');
      next.setAttribute('aria-current', 'true');
      activeLink = next;
      outlineLinks.forEach(function (l, i) {
        if (l.classList.contains('nav-h3')) l.classList.toggle('open', outlineParent[i] === outlineParent[idx]);
      });
      if (window.innerWidth > 680) next.scrollIntoView({ block: 'nearest' });
    };
    var io = new IntersectionObserver(spy, { rootMargin: '0px 0px -70% 0px' });
    outlineHeads.forEach(function (h) { io.observe(h); });
    window.addEventListener('hashchange', function () { pinHash(); spy(); });
    ['wheel', 'touchmove', 'keydown'].forEach(function (e) {
      window.addEventListener(e, function () { pinned = -1; }, { passive: true });
    });
    pinHash();
    spy();
  }

  // "On this page" list: the sidebar outline is hidden behind the menu button on phones, so CSS shows this one only there.
  var tocHeads = document.querySelectorAll('main h2[id]:not(.item)');
  var lead = mainEl.querySelector('h1 + p') || mainEl.querySelector('h1');
  if (lead && tocHeads.length >= 2) {
    var toc = document.createElement('nav');
    toc.className = 'page-toc';
    toc.setAttribute('aria-label', 'On this page');
    toc.innerHTML = '<span class="page-toc-title">On this page</span>';
    tocHeads.forEach(function (h) {
      var a = document.createElement('a');
      a.href = '#' + h.id;
      a.textContent = h.textContent.trim();
      toc.appendChild(a);
    });
    lead.after(toc);
  }

  document.querySelectorAll('main h1[id], main h2[id], main h3[id]').forEach(function (heading) {
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
      var done = function () {
        btn.classList.add('copied');
        document.getElementById('live-status').textContent = 'Link copied';
        setTimeout(function () {
          btn.classList.remove('copied');
          document.getElementById('live-status').textContent = '';
        }, 1200);
      };
      // clipboard is undefined on non-secure origins; fall back to putting the link in the address bar.
      if (navigator.clipboard) navigator.clipboard.writeText(url).then(done, function () {});
      else location.hash = heading.id;
    });
    heading.appendChild(btn);
  });
}());
