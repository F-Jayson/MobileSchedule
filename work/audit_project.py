from pathlib import Path
import json
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent.parent
docs = [root / "README.md", root / "CONTRIBUTING.md", *sorted((root / "docs").rglob("*.md"))]
errors = []
for path in docs:
    content = path.read_text(encoding="utf-8")
    if "\t" in content:
        errors.append(f"Tabs in {path.relative_to(root)}")
    for target in re.findall(r"\[[^\]]+\]\(([^)]+)\)", content):
        if target.startswith(("https://", "http://", "#")):
            continue
        if not (path.parent / target.split("#")[0]).exists():
            errors.append(f"Broken link in {path.relative_to(root)}: {target}")

tests = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
for path in (root / "app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"):
    suite = ET.parse(path).getroot()
    for key in tests:
        tests[key] += int(suite.attrib[key])

lint = ET.parse(root / "app/build/reports/lint-results-debug.xml").getroot()
lint_counts = {}
for issue in lint.findall("issue"):
    key = issue.attrib["severity"]
    lint_counts[key] = lint_counts.get(key, 0) + 1

schemas = list((root / "app/schemas").rglob("*.json"))
for schema in schemas:
    json.loads(schema.read_text(encoding="utf-8"))

print(json.dumps({"markdown_files": len(docs), "document_errors": errors,
                  "unit_tests": tests, "lint": lint_counts, "room_schemas": len(schemas)}, ensure_ascii=False))
raise SystemExit(1 if errors or tests["failures"] or tests["errors"] else 0)
