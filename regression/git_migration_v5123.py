from pathlib import Path
root=Path(__file__).resolve().parents[1]
app=(root/'app/build.gradle').read_text()
ignore=(root/'.gitignore').read_text()
checks={
 'baseline_version': "versionCode 118" in app and "5.12.3-engine-correctness" in app,
 'no_real_secrets_file': not (root/'secrets.properties').exists(),
 'example_exists': (root/'secrets.properties.example').exists(),
 'local_gradle_secret': "project.findProperty('BGG_TOKEN')" in app,
 'ci_env_secret': "System.getenv('BGG_TOKEN')" in app,
 'secret_ignored': 'secrets.properties' in ignore,
 'keystores_ignored': '*.jks' in ignore and '*.keystore' in ignore,
 'handoff_exists': (root/'AI_HANDOFF.md').exists(),
 'changelog_exists': (root/'CHANGELOG.md').exists(),
 'setup_exists': (root/'GITHUB_SETUP.md').exists(),
}
for k,v in checks.items(): print(('PASS' if v else 'FAIL'),k)
raise SystemExit(0 if all(checks.values()) else 1)
