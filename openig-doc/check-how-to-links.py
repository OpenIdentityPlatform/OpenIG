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

"""Check xref: macros and images in preprocessed How-To AsciiDoc.

Component-qualified xrefs are checked by the full Antora site build.
Local paths and module:page xrefs are supported; <<...>> references are rejected.
"""

from pathlib import Path
import re
import sys


def visible_text(text):
    """Verbatim blocks define neither AsciiDoc links nor AsciiDoc anchors."""
    return re.sub(r"^(/{4,}|-{4,}|\.{4,}|\+{4,})[ \t]*\n.*?^\1[ \t]*$", "",
                  text, flags=re.MULTILINE | re.DOTALL)


def main():
    source = (Path(sys.argv[1]) if len(sys.argv) > 1 else
              Path(__file__).parent / "target/asciidoc/source")
    articles = sorted((source / "how-to").glob("*.adoc"))
    if not articles:
        print(f"No generated How-To pages found in {source}; build the documentation first.")
        return 1

    errors = []
    checked = 0
    images = 0
    for article in articles:
        text = visible_text(article.read_text(encoding="utf-8"))
        for match in re.finditer(r"<<[^<>\n]+>>", text):
            errors.append(f"{article.name}: use xref: instead of {match.group(0)}")
        for match in re.finditer(r"xref:([^\s\[]+)\[", text):
            target = match.group(1)
            # Antora creates the component landing page; it has no source file here.
            if target == "ROOT:index.adoc" or re.match(r"[^/:]+:[^/:]*:", target):
                continue
            page, _, anchor = target.partition("#")
            checked += 1
            if anchor.startswith("++") and anchor.endswith("++"):
                anchor = anchor[2:-2]
            elif re.search(r"\w--\w", anchor):
                # Asciidoctor turns an intraword double hyphen into an em dash.
                errors.append(f"{article.name}: fragment needs ++...++: {target}")
                continue
            if re.match(r"[^/:]+:", page):
                module, page = page.split(":", 1)
                destination = source / module / page
            else:
                destination = article.parent / page if page else article
            if not destination.is_file():
                errors.append(f"{article.name}: missing page: {target}")
                continue
            if anchor:
                destination_text = visible_text(destination.read_text(encoding="utf-8"))
                ids = re.findall(r"^\[\[([^\],]+)", destination_text, re.MULTILINE)
                # In shorthand attributes, a dot starts a role and % starts an option.
                ids += re.findall(r"^\[[^\]#,\s]*#([^\].,%]+)", destination_text, re.MULTILINE)
                if anchor not in ids:
                    errors.append(f"{article.name}: broken anchor: {target}")

        imagesdir = re.search(r"^:imagesdir:\s*(.*)$", text, re.MULTILINE)
        image_root = article.parent / (imagesdir.group(1).strip() if imagesdir else "")
        for match in re.finditer(r"image::?([^\s\[]+)\[", text):
            target = match.group(1)
            if re.match(r"(?:https?:|data:)", target):
                continue
            images += 1
            if not (image_root / target).is_file():
                errors.append(f"{article.name}: missing image: {target}")

    for error in errors:
        print(error)
    print(f"Checked {checked} How-To xrefs in {len(articles)} pages; {len(errors)} errors.")
    print(f"Checked {images} local How-To images.")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
