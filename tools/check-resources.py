#!/usr/bin/env python3
"""Checks config.yml / plugin.yml parse, and that every key the climate code reads exists in config.yml."""
import re, sys, pathlib
import yaml

root = pathlib.Path(__file__).resolve().parent.parent
cfg = yaml.safe_load((root / "src/main/resources/config.yml").read_text())
plug = yaml.safe_load((root / "src/main/resources/plugin.yml").read_text())
print("plugin.yml and config.yml parse")

def has(d, dotted):
    cur = d
    for part in dotted.split("."):
        if not isinstance(cur, dict) or part not in cur:
            return False
        cur = cur[part]
    return True

bad = 0
src = (root / "src/main/java/com/nexusuniverse/seasons/climate/ClimateSettings.java").read_text()
keys = set(re.findall(r'c\(\)\.get\w+\("([a-z0-9.\-]+)"', src))
for k in sorted(keys):
    if not has(cfg, k):
        print("MISSING config key:", k)
        bad += 1
print("climate keys checked:", len(keys))

for cmd in ("forecast", "allergy", "nexusseasons"):
    if cmd not in plug.get("commands", {}):
        print("MISSING command in plugin.yml:", cmd)
        bad += 1
if str(plug.get("version")) != "${project.version}":
    print("plugin.yml version should be ${project.version}")
    bad += 1
sys.exit(1 if bad else 0)
