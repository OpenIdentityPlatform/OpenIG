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

"""Regression tests for How-To link validation."""

from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


CHECKER = Path(__file__).with_name("check-how-to-links.py")


class HowToLinksTest(unittest.TestCase):
    def check_source(self, article, files=None):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory)
            (source / "how-to").mkdir()
            (source / "how-to/page.adoc").write_text(article, encoding="utf-8")
            for name, text in (files or {}).items():
                destination = source / name
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.write_text(text, encoding="utf-8")
            return subprocess.run([sys.executable, str(CHECKER), str(source)],
                                  capture_output=True, text=True)

    def test_missing_page_fails(self):
        result = self.check_source("xref:missing.adoc[Missing]\n")
        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        self.assertIn("missing page", result.stdout)

    def test_same_page_fragment_is_checked(self):
        result = self.check_source("[[present]]\nxref:#missing[Missing]\n")
        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        self.assertIn("broken anchor", result.stdout)

    def test_listing_references_are_not_links(self):
        result = self.check_source("----\nxref:missing.adoc[Example]\nimage::absent.png[]\n----\n")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_image_resolves_from_imagesdir(self):
        result = self.check_source(":imagesdir: ../\nimage::images/how-to/picture.svg[Diagram]\n",
                                   {"images/how-to/picture.svg": "<svg/>"})
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_missing_image_fails(self):
        result = self.check_source(":imagesdir: ../\nimage::images/how-to/missing.png[]\n")
        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        self.assertIn("missing image", result.stdout)

    def test_component_xrefs_are_left_to_site_integration(self):
        result = self.check_source("xref:openam:how-to:page.adoc[OpenAM]\n"
                                   "xref:ROOT:index.adoc[OpenIG]\n")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_missing_generated_guide_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            result = subprocess.run([sys.executable, str(CHECKER), directory],
                                    capture_output=True, text=True)
        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        self.assertIn("No generated How-To pages", result.stdout)

    def check_link(self, anchor, definition):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory)
            (source / "how-to").mkdir()
            (source / "reference").mkdir()
            (source / "how-to/page.adoc").write_text(
                f"xref:../reference/module.adoc#{anchor}[Module]\n", encoding="utf-8")
            (source / "reference/module.adoc").write_text(definition, encoding="utf-8")
            return subprocess.run([sys.executable, str(CHECKER), str(source)],
                                  capture_output=True, text=True)

    def test_double_hyphen_between_word_characters_needs_passthrough(self):
        result = self.check_link("adaptive-risk--module-ref", "[[adaptive-risk--module-ref]]\n")
        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        self.assertIn("fragment needs ++...++", result.stdout)
        self.assertIn("Checked 1 How-To xrefs", result.stdout)

    def test_passthrough_preserves_double_hyphen(self):
        result = self.check_link("++adaptive-risk--module-ref++", "[[adaptive-risk--module-ref]]\n")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_double_hyphen_outside_words_does_not_need_passthrough(self):
        result = self.check_link("--module-ref", "[[--module-ref]]\n")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_anchors_inside_verbatim_blocks_are_not_destinations(self):
        for delimiter in ("////", "----", "...."):
            for definition in ("[[hidden]]", "[#hidden]"):
                with self.subTest(delimiter=delimiter, definition=definition):
                    result = self.check_link("hidden", f"{delimiter}\n{definition}\n{delimiter}\n")
                    self.assertEqual(1, result.returncode, result.stdout + result.stderr)
                    self.assertIn("broken anchor", result.stdout)

    def test_anchor_after_verbatim_block_is_a_destination(self):
        result = self.check_link("visible", "----\n[[hidden]]\n----\n[#visible]\n")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_block_closes_only_on_its_own_delimiter(self):
        for definition in ("-----\n----\n[[hidden]]\ntext\n-----\n",
                           "----\n....\n[[hidden]]\ntext\n----\n"):
            with self.subTest(definition=definition):
                result = self.check_link("hidden", definition)
                self.assertEqual(1, result.returncode, result.stdout + result.stderr)
                self.assertIn("broken anchor", result.stdout)

    def test_anchor_between_two_verbatim_blocks_is_a_destination(self):
        result = self.check_link("visible", "----\na\n----\n\n[[visible]]\ntext\n\n----\nb\n----\n")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_longer_and_padded_delimiters_open_and_close_blocks(self):
        for definition in ("-----\n[[hidden]]\ntext\n-----\n",
                           "----\n[[hidden]]\ntext\n----  \n",
                           "---- \n[[hidden]]\ntext\n----\t\n"):
            with self.subTest(definition=repr(definition)):
                result = self.check_link("hidden", definition)
                self.assertEqual(1, result.returncode, result.stdout + result.stderr)
                self.assertIn("broken anchor", result.stdout)


    def test_source_style_shorthand_id_is_a_destination(self):
        result = self.check_source("[source#present,yaml]\n----\na: b\n----\nxref:#present[Present]\n")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_passthrough_does_not_define_asciidoc_anchors(self):
        result = self.check_link("hidden", "++++\n[[hidden]]\n++++\n")
        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        self.assertIn("broken anchor", result.stdout)

    def test_passthrough_references_are_not_links(self):
        result = self.check_source("++++\nxref:missing.adoc[Example]\nimage::absent.png[]\n++++\n")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_module_qualified_xref_resolves_from_source_root(self):
        result = self.check_source("xref:reference:page.adoc#present[Present]\n",
                                   {"reference/page.adoc": "[[present]]\n== Title\n"})
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_module_qualified_xref_checks_fragments(self):
        result = self.check_source("xref:reference:page.adoc#missing[Missing]\n",
                                   {"reference/page.adoc": "[[present]]\n== Title\n"})
        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        self.assertIn("broken anchor", result.stdout)

    def test_angle_references_are_rejected(self):
        for reference in ("<<present>>", "<<page.adoc#present,Text>>"):
            with self.subTest(reference=reference):
                result = self.check_source("[[present]]\n== Title\n" + reference + "\n")
                self.assertEqual(1, result.returncode, result.stdout + result.stderr)
                self.assertIn("use xref:", result.stdout)

    def test_listing_angle_references_are_not_links(self):
        result = self.check_source("----\n<<example>>\n----\n")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_shorthand_role_and_option_are_not_part_of_the_id(self):
        result = self.check_source("[#present.role%option]\n== Title\nxref:#present[Present]\n")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_reftext_is_not_part_of_the_id(self):
        result = self.check_source("[[present,Present text]]\n== Title\nxref:#present[Present]\n")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_remote_and_data_images_are_skipped(self):
        result = self.check_source("image::https://example.com/a.png[]\nimage:data:image/png;base64,AAAA[]\n")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_image_without_imagesdir_resolves_next_to_the_page(self):
        result = self.check_source("image::picture.svg[]\n", {"how-to/picture.svg": "<svg/>"})
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def wiki_destinations(self):
        fixture = Path(__file__).parent / "src/test/resources/how-to-wiki-destinations.txt"
        destinations = [line for line in fixture.read_text(encoding="utf-8").splitlines()
                        if line and not line.startswith("#")]
        self.assertEqual(85, len(set(destinations)))
        return destinations

    def check_wiki_destinations(self, rename_anchor=False):
        doc = Path(__file__).parent
        destinations = self.wiki_destinations()
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "source"
            shutil.copytree(doc / "src/main/asciidoc", source)
            (source / "how-to/wiki-compatibility.adoc").write_text(
                "\n".join(f"xref:{page}#++{anchor}++[Section]"
                          for page, anchor in (target.split("#", 1) for target in destinations)),
                encoding="utf-8")
            if rename_anchor:
                page = source / "how-to/how-to-integrate-openig-and-message-brokers.adoc"
                page.write_text(page.read_text(encoding="utf-8").replace(
                    "[[how-to-integrate-openig-and-message-brokers-introduction]]",
                    "[[renamed-introduction]]"), encoding="utf-8")
            return subprocess.run([sys.executable, str(CHECKER), str(source)],
                                  capture_output=True, text=True)

    def test_original_wiki_destinations_exist(self):
        result = self.check_wiki_destinations()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_renamed_wiki_destination_fails(self):
        result = self.check_wiki_destinations(rename_anchor=True)
        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        self.assertIn("broken anchor", result.stdout)
        self.assertIn("how-to-integrate-openig-and-message-brokers-introduction", result.stdout)


if __name__ == "__main__":
    unittest.main()
