// Example user script for DBG Browser
// Match pattern suggestion: <all_urls>

(function() {
  var badge = document.createElement('div');
  badge.textContent = 'DBG user script active';
  badge.style.position = 'fixed';
  badge.style.left = '12px';
  badge.style.bottom = '12px';
  badge.style.zIndex = '2147483647';
  badge.style.padding = '8px 12px';
  badge.style.borderRadius = '999px';
  badge.style.background = '#0f3d3e';
  badge.style.color = '#fff';
  badge.style.font = '12px sans-serif';
  badge.style.boxShadow = '0 10px 24px rgba(0,0,0,.22)';
  document.documentElement.appendChild(badge);

  console.log('DBG example user script running on', location.href);
})();
