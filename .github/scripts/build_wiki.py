"""Turn the repository docs into GitHub wiki pages.

The files in docs/ (and the README) are the single source of truth. This script copies them into
wiki form: file names become page names, links between docs become wiki links, and images and
repository files point at their GitHub URLs so they work from the wiki too.

Usage: python build_wiki.py <repo-root> <wiki-dir> <owner/repo>
"""
import re
import sys
from pathlib import Path

ROOT, OUT, REPO = Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3]
BLOB = f"https://github.com/{REPO}/blob/main/"
RAW = f"https://raw.githubusercontent.com/{REPO}/main/"
SITE = f"https://github.com/{REPO}/"

# docs file -> wiki page name
PAGES = {
    "INSTALL.md": "Installation",
    "USER-GUIDE.md": "User-Guide",
    "TV-TOOLS.md": "TV-Tools",
    "TROUBLESHOOTING.md": "Troubleshooting",
    "BUILDING.md": "Building-from-Source",
    "RELEASING.md": "Releasing",
}

LINK = re.compile(r"(\]\(|src=\")([^)\"\s]+)")


def rewrite(target: str) -> str:
    if re.match(r"^(https?:|mailto:|#)", target):
        return target
    path, _, anchor = target.partition("#")
    anchor = f"#{anchor}" if anchor else ""
    # Links that climb out of docs/ to github.com pages, e.g. ../../../releases/latest
    m = re.match(r"^(?:\.\./)+(releases|issues|pulls|actions)(.*)$", path)
    if m:
        return SITE + m.group(1) + m.group(2) + anchor
    name = Path(path).name
    if name in PAGES:
        return PAGES[name] + anchor
    clean = path.lstrip("./").removeprefix("../")
    if clean.startswith("images/"):
        clean = "docs/" + clean
    if clean.startswith("docs/images/"):
        return RAW + clean
    return BLOB + clean + anchor


def convert(text: str) -> str:
    return LINK.sub(lambda m: m.group(1) + rewrite(m.group(2)), text)


OUT.mkdir(parents=True, exist_ok=True)
for page in OUT.glob("*.md"):
    page.unlink()  # pages removed from docs/ disappear from the wiki too

home = (ROOT / "README.md").read_text(encoding="utf-8")
(OUT / "Home.md").write_text(convert(home), encoding="utf-8")
for source, page in PAGES.items():
    text = (ROOT / "docs" / source).read_text(encoding="utf-8")
    (OUT / f"{page}.md").write_text(convert(text), encoding="utf-8")

sidebar = ["**[Home](Home)**", "", "**Using Lumen**"]
sidebar += ["- [Installation](Installation)", "- [User guide](User-Guide)", "- [TV tools](TV-Tools)",
            "- [Troubleshooting](Troubleshooting)", "", "**Development**",
            "- [Building from source](Building-from-Source)", "- [Releasing](Releasing)",
            f"- [Contributing]({BLOB}CONTRIBUTING.md)", f"- [Changelog]({BLOB}CHANGELOG.md)", "",
            f"[Download the latest release]({SITE}releases/latest)"]
(OUT / "_Sidebar.md").write_text("\n".join(sidebar) + "\n", encoding="utf-8")
(OUT / "_Footer.md").write_text(
    f"This wiki is generated from the [`docs/`]({SITE}tree/main/docs) folder of the repository. "
    "To suggest a change, edit the file there and open a pull request.\n", encoding="utf-8")

print("\n".join(sorted(p.name for p in OUT.glob("*.md"))))
