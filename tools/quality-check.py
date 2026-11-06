#!/usr/bin/env python3
"""Portable structure/manifest smoke checks; intentionally not an Android SDK build."""
from pathlib import Path
import re, xml.etree.ElementTree as ET
root=Path(__file__).resolve().parent.parent
android=root/'app/src/main'
xml_files=list((android/'res').rglob('*.xml'))+[android/'AndroidManifest.xml']
for p in xml_files: ET.parse(p)
manifest=(android/'AndroidManifest.xml').read_text()
assert 'android.permission.INTERNET' not in manifest, 'Internet permission must not be requested'
assert '.QuarantineActivity' in manifest and '.MainActivity' in manifest
version=(root/'app/build.gradle').read_text()
assert re.search(r'versionCode\s+53\b',version) and "versionName '5.0.0-rc2.2'" in version
assert re.search(r'(\./gradlew|gradle) --no-daemon --stacktrace assembleDebug',(root/'.github/workflows/android.yml').read_text())
assert (root/'.circleci/config.yml').is_file()
assert 'FileAlreadyExistsException' in (android/'java/com/clearos/ultra/TrashStore.java').read_text()
print(f'PASS: {len(xml_files)} XML, manifest privacy, build metadata, CI and restoration checks')
