"""Validate the Gradle beta versionName contract without pinning its source syntax."""

import re


_FALLBACK_ASSIGNMENT = re.compile(
    r"^\s*versionName\s+releaseVersionName\s*\?:\s*'(?P<version>[^']+)'",
    re.MULTILINE,
)
_VERSION_OVERRIDE = re.compile(
    r"^\s*def releaseVersionName = System\.getenv\('LUDO_VERSION_NAME'\)\s*$",
    re.MULTILINE,
)
_OVERRIDE_PATTERN = re.compile(r"releaseVersionName\s*==~\s*/(?P<pattern>[^/\r\n]+)/")
_BETA_FALLBACK = re.compile(r"5\.12\.\d{1,4}-[a-z0-9]+(?:-[a-z0-9]+)*")


def has_valid_beta_version_contract(build_script: str) -> bool:
    """Check the stable 5.12 fallback and constrained optional beta override."""
    assignment = _FALLBACK_ASSIGNMENT.search(build_script)
    if not assignment or not _VERSION_OVERRIDE.search(build_script):
        return False

    if not _BETA_FALLBACK.fullmatch(assignment.group("version")):
        return False

    override = _OVERRIDE_PATTERN.search(build_script)
    if not override or "Invalid LUDO_VERSION_NAME" not in build_script:
        return False

    try:
        allowed = re.compile(override.group("pattern"))
    except re.error:
        return False

    return (
        allowed.fullmatch("5.12.1-beta") is not None
        and allowed.fullmatch("5.12.1234-beta") is not None
        and allowed.fullmatch("5.12.12345-beta") is None
        and allowed.fullmatch("5.13.1-beta") is None
    )
