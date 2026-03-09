(function() {
  if (window.__dbgIdOverlayInstalled) return;
  window.__dbgIdOverlayInstalled = true;

  var state = {
    logs: [],
    minimized: false,
    left: null,
    top: null,
    generation: 0
  };

  try {
    var stored = localStorage.getItem('__dbg_overlay_pos');
    if (stored) {
      var parsed = JSON.parse(stored);
      if (parsed) {
        state.left = parsed.left;
        state.top = parsed.top;
      }
    }
  } catch (e) {}

  function safeString(value) {
    if (typeof value === 'string') return value;
    try {
      return JSON.stringify(value, null, 2);
    } catch (e) {
      return String(value);
    }
  }

  function normalizeHeaders(headers) {
    if (!headers) return {};
    if (typeof Headers !== 'undefined' && headers instanceof Headers) {
      var result = {};
      headers.forEach(function(value, key) {
        result[key] = value;
      });
      return result;
    }
    if (Object.prototype.toString.call(headers) === '[object Array]') {
      var arrayResult = {};
      for (var i = 0; i < headers.length; i++) {
        var pair = headers[i];
        if (pair && pair.length >= 2) {
          arrayResult[pair[0]] = pair[1];
        }
      }
      return arrayResult;
    }
    return headers;
  }

  function headersToText(headers) {
    var normalized = normalizeHeaders(headers);
    if (typeof normalized === 'string') return normalized;
    var lines = [];
    for (var key in normalized) {
      if (Object.prototype.hasOwnProperty.call(normalized, key)) {
        lines.push(key + ': ' + normalized[key]);
      }
    }
    return lines.join('\n');
  }

  function shouldIncludeResponseBody(contentType) {
    if (!contentType) return false;
    var type = String(contentType).toLowerCase();
    if (type.indexOf('application/json') >= 0) return true;
    if (type.indexOf('text/json') >= 0) return true;
    if (type.indexOf('text/html') >= 0) return true;
    if (type.indexOf('application/xhtml+xml') >= 0) return true;
    return false;
  }

  function buildRawRequest(method, url, headers, payload) {
    var parts = [];
    parts.push('REQUEST URI: ' + (url || ''));
    parts.push('METHOD: ' + (method || 'GET'));
    parts.push('HEADERS:\n' + (headersToText(headers) || '(none)'));
    if (String(method || 'GET').toUpperCase() === 'POST') {
      parts.push('PAYLOAD:\n' + safeString(payload || ''));
    }
    return parts.join('\n\n');
  }

  function buildRawResponse(statusLine, headers, body) {
    var parts = [];
    parts.push(statusLine || 'RESPONSE');
    parts.push('HEADERS:\n' + (headersToText(headers) || '(none)'));
    if (body) {
      parts.push('BODY:\n' + body);
    } else {
      parts.push('BODY:\n(skipped)');
    }
    return parts.join('\n\n');
  }

  function copyText(text) {
    var value = safeString(text);
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(value).catch(function() {
        window.prompt('Copy', value);
      });
    } else {
      window.prompt('Copy', value);
    }
  }

  function render() {
    var root = document.getElementById('dbg-id-overlay');
    if (!root) {
      root = document.createElement('div');
      root.id = 'dbg-id-overlay';
      root.innerHTML =
        '<div id="dbg-id-head">' +
        '<strong>DBG Network</strong>' +
        '<div>' +
        '<button id="dbg-id-min">_</button>' +
        '<button id="dbg-id-clear">clear</button>' +
        '</div>' +
        '</div>' +
        '<div id="dbg-id-body"></div>';
      document.documentElement.appendChild(root);

      var style = document.createElement('style');
      style.textContent =
        '#dbg-id-overlay{position:fixed;right:12px;bottom:12px;width:340px;max-height:55vh;z-index:2147483647;background:#0f1720;color:#f4f7fb;border:1px solid #31424d;border-radius:14px;box-shadow:0 10px 30px rgba(0,0,0,.35);font:12px/1.4 monospace;overflow:hidden;touch-action:none;}' +
        '#dbg-id-head{display:flex;justify-content:space-between;align-items:center;padding:10px 12px;background:#172330;border-bottom:1px solid #31424d;cursor:move;}' +
        '#dbg-id-head button{margin-left:6px;background:#243645;color:#fff;border:0;border-radius:8px;padding:4px 8px;}' +
        '#dbg-id-body{overflow:auto;max-height:48vh;padding:8px;display:block;}' +
        '#dbg-id-body.hidden{display:none;}' +
        '.dbg-id-item{border:1px solid #2a3a45;border-radius:10px;padding:8px;margin-bottom:8px;background:#101a23;}' +
        '.dbg-id-title{color:#86d7ff;font-weight:bold;word-break:break-all;}' +
        '.dbg-id-sub{color:#b7c2cb;margin-top:4px;white-space:pre-wrap;word-break:break-word;}' +
        '.dbg-id-actions{margin-top:6px;}' +
        '.dbg-id-actions button{background:#f26b4b;color:#fff;border:0;border-radius:8px;padding:4px 8px;}';
      document.documentElement.appendChild(style);

      bindHeaderButton('dbg-id-clear', function() {
        state.logs = [];
        state.generation++;
        render();
      });
      bindHeaderButton('dbg-id-min', function() {
        state.minimized = !state.minimized;
        render();
      });

      makeDraggable(root, document.getElementById('dbg-id-head'));
    }

    if (state.left !== null && state.top !== null) {
      root.style.left = state.left + 'px';
      root.style.top = state.top + 'px';
      root.style.right = 'auto';
      root.style.bottom = 'auto';
    }

    var body = document.getElementById('dbg-id-body');
    body.className = state.minimized ? 'hidden' : '';
    if (state.minimized) {
      return;
    }
    body.innerHTML = '';
    if (!state.logs.length) {
      var empty = document.createElement('div');
      empty.className = 'dbg-id-sub';
      empty.textContent = 'No captured requests.';
      body.appendChild(empty);
      return;
    }
    for (var i = state.logs.length - 1; i >= 0; i--) {
      var item = state.logs[i];
      var card = document.createElement('div');
      card.className = 'dbg-id-item';
      var title = document.createElement('div');
      title.className = 'dbg-id-title';
      title.textContent = item.type + ' ' + item.url;
      var subtitle = document.createElement('div');
      subtitle.className = 'dbg-id-sub';
      subtitle.textContent = item.detail;
      var actions = document.createElement('div');
      actions.className = 'dbg-id-actions';
      var copy = document.createElement('button');
      copy.textContent = 'copy payload';
      copy.onclick = (function(payload) {
        return function() {
          copyText(payload);
        };
      })(item.payload);
      actions.appendChild(copy);
      if (item.requestRaw) {
        var copyRequest = document.createElement('button');
        copyRequest.textContent = 'copy request';
        copyRequest.style.marginLeft = '6px';
        copyRequest.onclick = (function(raw) {
          return function() {
            copyText(raw);
          };
        })(item.requestRaw);
        actions.appendChild(copyRequest);
      }
      if (item.responseRaw) {
        var copyResponse = document.createElement('button');
        copyResponse.textContent = 'copy response';
        copyResponse.style.marginLeft = '6px';
        copyResponse.onclick = (function(raw) {
          return function() {
            copyText(raw);
          };
        })(item.responseRaw);
        actions.appendChild(copyResponse);
      }
      card.appendChild(title);
      card.appendChild(subtitle);
      card.appendChild(actions);
      body.appendChild(card);
    }
  }

  function bindHeaderButton(id, handler) {
    var button = document.getElementById(id);
    if (!button) return;
    button.onclick = function(e) {
      if (e) {
        if (e.preventDefault) e.preventDefault();
        if (e.stopPropagation) e.stopPropagation();
      }
      handler();
      return false;
    };
    button.addEventListener('touchstart', function(e) {
      if (e.preventDefault) e.preventDefault();
      if (e.stopPropagation) e.stopPropagation();
    }, false);
    button.addEventListener('mousedown', function(e) {
      if (e.preventDefault) e.preventDefault();
      if (e.stopPropagation) e.stopPropagation();
    }, false);
  }

  function makeDraggable(root, handle) {
    var dragging = false;
    var startX = 0;
    var startY = 0;
    var baseLeft = 0;
    var baseTop = 0;

    function pointerDown(clientX, clientY) {
      var rect = root.getBoundingClientRect();
      dragging = true;
      startX = clientX;
      startY = clientY;
      baseLeft = rect.left;
      baseTop = rect.top;
      root.style.left = baseLeft + 'px';
      root.style.top = baseTop + 'px';
      root.style.right = 'auto';
      root.style.bottom = 'auto';
    }

    function pointerMove(clientX, clientY) {
      if (!dragging) return;
      state.left = Math.max(0, baseLeft + (clientX - startX));
      state.top = Math.max(0, baseTop + (clientY - startY));
      root.style.left = state.left + 'px';
      root.style.top = state.top + 'px';
    }

    function pointerUp() {
      if (!dragging) return;
      dragging = false;
      try {
        localStorage.setItem('__dbg_overlay_pos', JSON.stringify({
          left: state.left,
          top: state.top
        }));
      } catch (e) {}
    }

    handle.addEventListener('mousedown', function(e) {
      pointerDown(e.clientX, e.clientY);
      e.preventDefault();
    });
    document.addEventListener('mousemove', function(e) {
      pointerMove(e.clientX, e.clientY);
    });
    document.addEventListener('mouseup', pointerUp);

    handle.addEventListener('touchstart', function(e) {
      var t = e.touches && e.touches[0];
      if (!t) return;
      pointerDown(t.clientX, t.clientY);
      e.preventDefault();
    }, false);
    document.addEventListener('touchmove', function(e) {
      var t = e.touches && e.touches[0];
      if (!t) return;
      pointerMove(t.clientX, t.clientY);
      e.preventDefault();
    }, false);
    document.addEventListener('touchend', pointerUp);
  }

  function push(type, url, detail, payload, requestRaw, responseRaw, generation) {
    if (typeof generation === 'number' && generation !== state.generation) {
      return;
    }
    state.logs.push({
      type: type,
      url: url,
      detail: detail,
      payload: payload,
      requestRaw: requestRaw,
      responseRaw: responseRaw
    });
    if (state.logs.length > 80) {
      state.logs.shift();
    }
    render();
  }

  var originalFetch = window.fetch;
  if (originalFetch) {
    window.fetch = function(input, init) {
      var generation = state.generation;
      var url = typeof input === 'string' ? input : (input && input.url) || '';
      var method = (init && init.method) || 'GET';
      var headers = normalizeHeaders((init && init.headers) || {});
      var body = (init && init.body) || '';
      var requestRaw = buildRawRequest(method, url, headers, body);
      return originalFetch.apply(this, arguments).then(function(response) {
        var responseHeaders = {};
        try {
          response.headers.forEach(function(value, key) {
            responseHeaders[key] = value;
          });
        } catch (e) {}
        var contentType = responseHeaders['content-type'] || responseHeaders['Content-Type'] || '';
        var statusLine = 'STATUS: ' + response.status + ' ' + (response.statusText || '');
        if (response.clone && shouldIncludeResponseBody(contentType)) {
          return response.clone().text().then(function(text) {
            push(
              'fetch ' + method,
              url,
              'status: ' + response.status + '\nrequest headers: ' + safeString(headers) + '\nresponse headers: ' + safeString(responseHeaders),
              { headers: headers, payload: body },
              requestRaw,
              buildRawResponse(statusLine, responseHeaders, text),
              generation
            );
            return response;
          }).catch(function() {
            push(
              'fetch ' + method,
              url,
              'status: ' + response.status + '\nrequest headers: ' + safeString(headers) + '\nresponse headers: ' + safeString(responseHeaders),
              { headers: headers, payload: body },
              requestRaw,
              buildRawResponse(statusLine, responseHeaders, ''),
              generation
            );
            return response;
          });
        }
        push(
          'fetch ' + method,
          url,
          'status: ' + response.status + '\nrequest headers: ' + safeString(headers) + '\nresponse headers: ' + safeString(responseHeaders),
          { headers: headers, payload: body },
          requestRaw,
          buildRawResponse(statusLine, responseHeaders, ''),
          generation
        );
        return response;
      }).catch(function(error) {
        push(
          'fetch ' + method,
          url,
          'request failed: ' + safeString(error),
          { headers: headers, payload: body },
          requestRaw,
          '',
          generation
        );
        throw error;
      });
    };
  }

  var originalOpen = XMLHttpRequest.prototype.open;
  var originalSend = XMLHttpRequest.prototype.send;
  var originalHeader = XMLHttpRequest.prototype.setRequestHeader;
  XMLHttpRequest.prototype.open = function(method, url) {
    this.__dbgMethod = method;
    this.__dbgUrl = url;
    this.__dbgHeaders = {};
    return originalOpen.apply(this, arguments);
  };
  XMLHttpRequest.prototype.setRequestHeader = function(key, value) {
    if (!this.__dbgHeaders) this.__dbgHeaders = {};
    this.__dbgHeaders[key] = value;
    return originalHeader.apply(this, arguments);
  };
  XMLHttpRequest.prototype.send = function(body) {
    var self = this;
    var generation = state.generation;
    var method = this.__dbgMethod || 'GET';
    var url = this.__dbgUrl || location.href;
    var requestHeaders = this.__dbgHeaders || {};
    var requestRaw = buildRawRequest(method, url, requestHeaders, body || '');

    function finalize() {
      var rawHeaders = '';
      var responseHeaders = {};
      try {
        rawHeaders = self.getAllResponseHeaders() || '';
      } catch (e) {}
      if (rawHeaders) {
        var lines = rawHeaders.split(/\r?\n/);
        for (var i = 0; i < lines.length; i++) {
          var line = lines[i];
          var idx = line.indexOf(':');
          if (idx > 0) {
            var key = line.substring(0, idx).trim();
            var value = line.substring(idx + 1).trim();
            responseHeaders[key] = value;
          }
        }
      }
      var contentType = responseHeaders['Content-Type'] || responseHeaders['content-type'] || '';
      var responseBody = '';
      if (shouldIncludeResponseBody(contentType)) {
        try {
          if (!self.responseType || self.responseType === '' || self.responseType === 'text') {
            responseBody = self.responseText || '';
          }
        } catch (e) {}
      }
      push(
        'xhr ' + method,
        url,
        'status: ' + self.status + '\nrequest headers: ' + safeString(requestHeaders) + '\nresponse headers: ' + safeString(responseHeaders),
        { headers: requestHeaders, payload: body || '' },
        requestRaw,
        buildRawResponse('STATUS: ' + self.status + ' ' + (self.statusText || ''), responseHeaders, responseBody),
        generation
      );
    }

    if (this.addEventListener) {
      this.addEventListener('loadend', finalize);
    } else {
      var previous = this.onreadystatechange;
      this.onreadystatechange = function() {
        if (self.readyState === 4) {
          finalize();
        }
        if (previous) {
          previous.apply(self, arguments);
        }
      };
    }
    return originalSend.apply(this, arguments);
  };

  render();
})();
