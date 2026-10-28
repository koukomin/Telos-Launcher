import os, re, glob

files = glob.glob("app/ui/src/main/java/de/mm20/launcher2/ui/settings/**/*.kt", recursive=True)

for path in files:
    with open(path, 'r') as f:
        content = f.read()
    
    # Match PreferenceScreen(title = stringResource(...))
    # or PreferenceScreen(title = stringResource(...) )
    pattern = r'PreferenceScreen\(\s*title\s*=\s*(stringResource\(.*?\))\s*\)'
    replacement = r'PreferenceScreen(title = { Text(\1) })'
    
    new_content = re.sub(pattern, replacement, content)
    if new_content != content:
        with open(path, 'w') as f:
            f.write(new_content)
        print(f"Updated {path}")

