#!/bin/sh
# The contents of this file are subject to the terms of the Common Development and
# Distribution License (the License). You may not use this file except in compliance with the
# License.
#
# You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
# specific language governing permission and limitations under the License.
#
# When distributing Covered Software, include this CDDL Header Notice in each file and include
# the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
# Header, with the fields enclosed by brackets [] replaced by your own identifying
# information: "Portions copyright [year] [name of copyright owner]".
#
# Copyright 2026 3A Systems, LLC.

set -eu

if [ -e openig-config ]; then
    printf '%s\n' 'openig-config already exists. Run this script in a new demo directory.' >&2
    exit 1
fi
mkdir -p openig-config/routes openig-config/static

cat > openig-config/admin.json <<'EOF'
{
  "prefix": "openig",
  "mode": "PRODUCTION"
}
EOF

cat > openig-config/config.json <<'EOF'
{
  "handler": {
    "type": "Router",
    "name": "_router"
  }
}
EOF

cat > openig-config/routes/10-ui.json <<'EOF'
{
  "name": "websocket-ui",
  "condition": "${request.uri.path == '/ui'}",
  "handler": {
    "type": "StaticResponseHandler",
    "config": {
      "status": 200,
      "headers": {
        "Content-Type": [
          "text/html; charset=UTF-8"
        ]
      },
      "entity": "${read('/var/openig/config/static/ws-client.html')}"
    }
  }
}
EOF

cat > openig-config/routes/20-websocket.json <<'EOF'
{
  "name": "websocket-proxy",
  "condition": "${request.uri.path == '/ws-handler'}",
  "handler": {
    "type": "Chain",
    "config": {
      "filters": [
        {
          "type": "TokenTransformationFilter",
          "config": {
            "openamUri": "${system['openam']}",
            "realm": "/",
            "instance": "jwt",
            "from": "OPENAM",
            "to": "OPENIDCONNECT",
            "idToken": "${request.cookies['iPlanetDirectoryPro'][0].value}"
          }
        },
        {
          "type": "ConditionEnforcementFilter",
          "config": {
            "condition": "${not empty contexts.sts.issuedToken}",
            "failureHandler": {
              "type": "StaticResponseHandler",
              "config": {
                "status": 401,
                "headers": {
                  "Content-Type": [
                    "application/json"
                  ]
                },
                "entity": "{\"error\":\"Unauthorized\"}"
              }
            }
          }
        },
        {
          "type": "HeaderFilter",
          "config": {
            "messageType": "REQUEST",
            "remove": [
              "Authorization",
              "JWT",
              "Sec-WebSocket-Key",
              "Sec-WebSocket-Version",
              "Origin"
            ],
            "add": {
              "Authorization": [
                "Bearer ${contexts.sts.issuedToken}"
              ]
            }
          }
        }
      ],
      "handler": {
        "type": "DispatchHandler",
        "config": {
          "bindings": [
            {
              "handler": "ClientHandler",
              "baseURI": "${system['ws.secured']}"
            }
          ]
        }
      }
    }
  }
}
EOF

cat > openig-config/static/ws-client.html <<'EOF'
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>WebSocket echo client</title>
</head>
<body>
  <h1>WebSocket echo client</h1>
  <button type="button" id="connect">Connect</button>
  <button type="button" id="send">Send message</button>
  <pre id="log"></pre>
  <script>
    const log = document.getElementById('log');
    const append = (message) => {
      log.textContent += message + '\n';
    };

    let socket;
    document.getElementById('connect').onclick = () => {
      if (socket && socket.readyState < WebSocket.CLOSING) return;
      const scheme = location.protocol === 'https:' ? 'wss:' : 'ws:';
      socket = new WebSocket(scheme + '//' + location.host + '/ws-handler');
      socket.onmessage = (event) => append(event.data);
      socket.onopen = () => append('Connected');
      socket.onerror = () => append('Connection error');
      socket.onclose = () => append('Connection closed');
    };
    document.getElementById('send').onclick = () => {
      if (socket && socket.readyState === WebSocket.OPEN) socket.send('Test message');
    };
  </script>
</body>
</html>
EOF
