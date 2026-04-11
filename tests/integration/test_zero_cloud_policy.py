"""
Zero-Cloud Policy — Integration test

Scans the entire project for banned cloud SDK references in build files.
This is the Python counterpart of scripts/audit_deps.sh for CI pipelines.
"""

import os
import re
import pytest

PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))

BANNED_PATTERNS = [
    r"firebase",
    r"com\.google\.firebase",
    r"com\.google\.android\.gms",
    r"com\.google\.cloud",
    r"com\.amazonaws",
    r"software\.amazon\.awssdk",
    r"com\.azure",
    r"com\.microsoft\.azure",
    r"io\.sentry",
    r"com\.crashlytics",
    r"com\.bugsnag",
    r"com\.amplitude",
    r"com\.mixpanel",
    r"com\.segment",
    r"com\.appsflyer",
    r"com\.facebook\.android",
    r"latest\.release",
    r"latest\.integration",
]

BUILD_FILE_EXTENSIONS = {".gradle", ".gradle.kts", ".toml"}


def _find_build_files():
    """Find all Gradle/TOML build files in the project."""
    build_files = []
    for root, _dirs, files in os.walk(PROJECT_ROOT):
        # Skip build directories and hidden dirs
        if any(skip in root for skip in ["/build/", "/.gradle/", "/.git/", "/.idea/"]):
            continue
        for f in files:
            if any(f.endswith(ext) for ext in BUILD_FILE_EXTENSIONS):
                build_files.append(os.path.join(root, f))
    return build_files


class TestZeroCloudPolicy:
    """Ensure no cloud dependencies exist in any build file."""

    @pytest.fixture
    def build_files(self):
        return _find_build_files()

    @pytest.fixture
    def all_build_content(self, build_files):
        contents = {}
        for path in build_files:
            with open(path, "r", encoding="utf-8", errors="ignore") as f:
                contents[path] = f.read()
        return contents

    @pytest.mark.parametrize("pattern", BANNED_PATTERNS)
    def test_no_banned_dependency(self, all_build_content, pattern):
        """Verify no build file contains a banned cloud SDK pattern."""
        violations = []
        for path, content in all_build_content.items():
            if re.search(pattern, content, re.IGNORECASE):
                rel_path = os.path.relpath(path, PROJECT_ROOT)
                violations.append(rel_path)

        assert not violations, (
            f"Zero-Cloud violation: pattern '{pattern}' found in: {violations}"
        )

    def test_no_version_wildcards(self, all_build_content):
        """Verify no build file uses + wildcard versions."""
        violations = []
        for path, content in all_build_content.items():
            # Match version strings like "1.2.+" or version = "+"
            if re.search(r"""['"][^'"]*\+['"]""", content):
                rel_path = os.path.relpath(path, PROJECT_ROOT)
                violations.append(rel_path)

        assert not violations, (
            f"Version wildcard (+) found in: {violations}"
        )

    def test_build_files_exist(self, build_files):
        """Sanity check: at least one build file should exist."""
        assert len(build_files) > 0, "No build files found — project may not be scaffolded"
