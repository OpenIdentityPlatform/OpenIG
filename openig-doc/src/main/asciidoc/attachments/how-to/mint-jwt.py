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

import argparse
import base64
import json
import subprocess
import time


def base64url(data):
    return base64.urlsafe_b64encode(data).rstrip(b"=")


def encode_json(value):
    return base64url(json.dumps(value, separators=(",", ":")).encode())


parser = argparse.ArgumentParser(description="Sign a five-minute JWT for the local OpenIG demo.")
parser.add_argument("private_key", help="PEM-encoded RSA private key")
parser.add_argument("subject", help="User identifier")
parser.add_argument("role", help="User role")
args = parser.parse_args()

now = int(time.time())
header = encode_json({"alg": "RS256", "typ": "JWT"})
claims = encode_json({
    "iss": "sample-service",
    "aud": "sample-api",
    "sub": args.subject,
    "name": args.subject,
    "role": args.role,
    "iat": now,
    "exp": now + 300,
})
signing_input = header + b"." + claims
signature = subprocess.run(
    ["openssl", "dgst", "-sha256", "-sign", args.private_key],
    input=signing_input, stdout=subprocess.PIPE, check=True,
).stdout
print((signing_input + b"." + base64url(signature)).decode("ascii"))
