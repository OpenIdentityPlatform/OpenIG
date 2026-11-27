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

"""Check local links and fragments emitted by Antora on OpenIG pages."""

import argparse
from functools import lru_cache
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit


class Page(HTMLParser):
    def __init__(self, path):
        super().__init__()
        self.anchors = set()
        self.links = set()
        self.feed(path.read_text(encoding="utf-8"))

    def handle_starttag(self, tag, attributes):
        attributes = dict(attributes)
        if attributes.get("id"):
            self.anchors.add(attributes["id"])
        if tag == "a":
            if attributes.get("name"):
                self.anchors.add(attributes["name"])
            if attributes.get("href"):
                self.links.add(attributes["href"])


def local_target(root, page, path):
    if not path:
        return page
    path = unquote(path)
    target = root / path.lstrip("/") if path.startswith("/") else page.parent / path
    target = target.resolve()
    if not target.is_relative_to(root):
        return None
    for candidate in (target, target / "index.html", target.with_name(target.name + ".html")):
        if candidate.is_file():
            return candidate
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("site", type=Path, help="Antora output directory containing openig/")
    root = parser.parse_args().site.resolve()
    pages = sorted((root / "openig").rglob("*.html"))
    if not pages:
        print(f"No rendered OpenIG pages in {root}")
        return 1

    load_page = lru_cache(maxsize=None)(Page)
    failures = 0
    for page in pages:
        for link in sorted(load_page(page).links):
            url = urlsplit(link)
            if url.scheme or url.netloc:
                continue
            target = local_target(root, page, url.path)
            problem = None
            if target is None:
                problem = "missing target"
            elif (url.fragment and target.suffix == ".html" and
                  unquote(url.fragment) not in load_page(target).anchors):
                problem = "missing fragment"
            if problem:
                print(f"{page.relative_to(root)}: {problem}: {link}")
                failures += 1

    print(f"Checked local links in {len(pages)} rendered OpenIG pages. {failures} errors.")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
