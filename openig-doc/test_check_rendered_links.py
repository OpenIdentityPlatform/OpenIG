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

from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


CHECKER = Path(__file__).with_name("check-rendered-links.py")


class RenderedLinksTest(unittest.TestCase):
    def check(self, pages):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name, body in pages.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(body, encoding="utf-8")
            return subprocess.run([sys.executable, CHECKER, root], capture_output=True, text=True)

    def test_extensionless_links_and_encoded_fragments(self):
        result = self.check({
            "openig/index.html": '<a href="guide/start#two%20words">Start</a>'
                                 '<a href="/openam/#overview">OpenAM</a>'
                                 '<a href="https://example.com/#external">External</a>',
            "openig/guide/start.html": '<h2 id="two words">Start</h2>'
                                       '<a href="#two%20words">Here</a>',
            "openam/index.html": '<h1 id="overview">OpenAM</h1>',
        })
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_a_page_can_exist_without_the_chapter_anchor(self):
        result = self.check({
            "openig/index.html": '<a href="guide/start#chapter-start">Start</a>',
            "openig/guide/start.html": '<h1>Start</h1>',
        })
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("missing fragment: guide/start#chapter-start", result.stdout)

    def test_missing_page_fails(self):
        result = self.check({"openig/index.html": '<a href="nonexistent#target">Start</a>'})
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("missing target: nonexistent#target", result.stdout)

    def test_missing_rendered_output_fails(self):
        result = self.check({})
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("No rendered OpenIG pages", result.stdout)
