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

if [ -e openig ]; then
    printf '%s\n' 'openig already exists. Run this script in a new demo directory.' >&2
    exit 1
fi

mkdir -p openig/config/routes openig/scripts/groovy

cat > openig/config/admin.json <<'EOF'
{
  "prefix": "openig",
  "mode": "PRODUCTION"
}
EOF

cat > openig/config/config.json <<'EOF'
{
  "handler": {
    "type": "Router",
    "name": "_router"
  }
}
EOF

cat > openig/config/routes/10-llm.json <<'EOF'
{
  "name": "ollama-chat",
  "condition": "${(request.method == 'POST') and matches(request.uri.path, '^/v1/chat/completions$')}",
  "handler": {
    "type": "Chain",
    "config": {
      "filters": [
        {
          "name": "RequestCleanupGuardRail",
          "type": "ScriptableFilter",
          "config": {
            "type": "application/x-groovy",
            "file": "RequestGuardRail.groovy",
            "args": {
              "systemPrompt": "You are a helpful assistant",
              "allowedModels": [
                "qwen2.5:0.5b"
              ],
              "maxInputLength": 10000
            }
          }
        },
        {
          "name": "LlmGuardRail",
          "type": "ScriptableFilter",
          "config": {
            "type": "application/x-groovy",
            "file": "LlmGuardRail.groovy",
            "args": {
              "model": "qwen2.5:0.5b",
              "modelUri": "${system['openai.api']}/v1/chat/completions"
            }
          }
        },
        {
          "name": "ResponseGuardRail",
          "type": "ScriptableFilter",
          "config": {
            "type": "application/x-groovy",
            "file": "ResponseGuardRail.groovy",
            "args": {
              "escapeHtml": true,
              "removeCodeBlocks": true
            }
          }
        }
      ],
      "handler": "LlmDispatchHandler"
    }
  },
  "heap": [
    {
      "name": "LlmDispatchHandler",
      "type": "DispatchHandler",
      "config": {
        "bindings": [
          {
            "handler": {
              "type": "ClientHandler",
              "config": {
                "connectionTimeout": "60 seconds",
                "soTimeout": "60 seconds"
              }
            },
            "baseURI": "${system['openai.api']}"
          }
        ]
      }
    }
  ]
}
EOF
