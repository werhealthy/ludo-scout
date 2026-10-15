"""Validate an explicitly supplied beta versionCode suffix for manual builds."""

from __future__ import annotations

import os
import re
import sys

BASE_VERSION_CODE = 1_000_000
MIN_INSTALLED_VERSION_CODE = 1_002_305
MAX_VERSION_CODE = 2_147_483_647


def derive_version_code(suffix: str) -> int:
    if not isinstance(suffix, str) or not re.fullmatch(r"[0-9]+", suffix):
        raise ValueError("version_code_suffix must contain decimal digits only")
    version_code = BASE_VERSION_CODE + int(suffix)
    if version_code <= MIN_INSTALLED_VERSION_CODE:
        raise ValueError(
            "version_code_suffix must be >2305 to exceed installed versionCode 1002305"
        )
    if version_code > MAX_VERSION_CODE:
        raise ValueError("versionCode exceeds Android's signed 32-bit range")
    return version_code


def main() -> int:
    try:
        version_code = derive_version_code(os.environ.get("VERSION_CODE_SUFFIX", ""))
    except ValueError as exc:
        print(str(exc), file=sys.stderr)
        return 1
    print(f"Validated manual versionCode={version_code}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
