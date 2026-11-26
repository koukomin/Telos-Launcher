import glob
import re

MAPPING = {
    r'actions/cache@v\d+': 'actions/cache@v4',
    r'actions/checkout@v\d+': 'actions/checkout@v4',
    r'actions/configure-pages@v\d+': 'actions/configure-pages@v5',
    r'actions/deploy-pages@v\d+': 'actions/deploy-pages@v4',
    r'actions/download-artifact@v\d+': 'actions/download-artifact@v4',
    r'actions/setup-go@v\d+': 'actions/setup-go@v5',
    r'actions/setup-java@v\d+': 'actions/setup-java@v4',
    r'actions/setup-node@v\d+': 'actions/setup-node@v4',
    r'actions/setup-python@v\d+': 'actions/setup-python@v5',
    r'actions/stale@v\d+': 'actions/stale@v9',
    r'actions/upload-artifact@v\d+': 'actions/upload-artifact@v4',
    r'actions/upload-pages-artifact@v\d+': 'actions/upload-pages-artifact@v3',
    r'gradle/actions/setup-gradle@v\d+': 'gradle/actions/setup-gradle@v4',
}

def fix_workflows():
    updated = 0
    for path in sorted(glob.glob('.github/workflows/*.yml')):
        with open(path, 'r', encoding='utf-8') as f:
            content = f.read()
        new_content = content
        for pattern, replacement in MAPPING.items():
            new_content = re.sub(pattern, replacement, new_content)
        if new_content != content:
            with open(path, 'w', encoding='utf-8') as f:
                f.write(new_content)
            updated += 1
            print(f'Updated {path}')
    print(f'Total workflow files updated: {updated}')

if __name__ == '__main__':
    fix_workflows()
