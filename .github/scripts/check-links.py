#!/usr/bin/env python3
"""Fail if a relative link or image in a Markdown file points to a file that does not exist."""
import glob
import os
import re
import sys

LINK = re.compile(r'\]\(([^)#\s]+)(?:#[^)]*)?\)')
SRC = re.compile(r'(?:src|href)="([^"#]+)"')

bad = 0
files = [f for f in glob.glob('**/*.md', recursive=True) if '/target/' not in f]
for f in files:
    text = open(f, encoding='utf-8').read()
    for target in LINK.findall(text) + SRC.findall(text):
        if target.startswith(('http://', 'https://', 'mailto:')):
            continue
        path = os.path.normpath(os.path.join(os.path.dirname(f), target))
        if not os.path.exists(path):
            print(f'BROKEN LINK in {f}: {target}')
            bad += 1
print(f'checked {len(files)} markdown files, broken links: {bad}')
sys.exit(1 if bad else 0)
