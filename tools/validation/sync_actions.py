import urllib.request
import json
import re
import glob
import os
import time

WORKFLOWS_DIR = '.github/workflows'
GITHUB_TOKEN = os.environ.get('GITHUB_TOKEN', '')

def get_latest_tag_and_sha(repo):
    # Ignore actions that aren't standard repos (like docker://)
    if repo.startswith('docker://') or repo.startswith('.'):
        return None, None

    url = f"https://api.github.com/repos/{repo}/tags"
    headers = {'User-Agent': 'Telos-CI-Bot'}
    if GITHUB_TOKEN:
        headers['Authorization'] = f'Bearer {GITHUB_TOKEN}'

    try:
        req = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(req) as response:
            tags = json.loads(response.read().decode())
            valid_tags = []
            for t in tags:
                name = t['name']
                # Match standard semver tags like v4, v4.2.2, 1.2.3, v2.0
                match = re.match(r'^v?(\d+)(\.\d+)*$', name)
                if match:
                    parts = [int(x) for x in name.lstrip('v').split('.')]
                    while len(parts) < 3: parts.append(0)
                    valid_tags.append((parts, name, t['commit']['sha']))

            if not valid_tags:
                return None, None

            # Sort by version numbers descending
            valid_tags.sort(key=lambda x: x[0], reverse=True)
            best_tag = valid_tags[0]

            # Find the major tag if exists (e.g. v4 instead of v4.2.2) to use as the comment
            major_tag = f"v{best_tag[0][0]}"

            return major_tag, best_tag[2]
    except Exception as e:
        print(f"Error fetching {repo}: {e}")
        return None, None

def update_workflows():
    # Regex to match uses: owner/repo@sha # vX or uses: owner/repo@vX
    uses_pattern = re.compile(r'(uses:\s+)([a-zA-Z0-9_.-]+/[a-zA-Z0-9_.-]+)(@)([a-f0-9]+|v[\d.]+)(.*)')

    cache = {}
    updated_files = 0

    for yml in glob.glob(f'{WORKFLOWS_DIR}/*.yml'):
        with open(yml, 'r') as f:
            lines = f.readlines()

        changed = False
        new_lines = []
        for line in lines:
            match = uses_pattern.search(line)
            if match:
                prefix = match.group(1)
                repo = match.group(2)

                # Fetch latest if not in cache
                if repo not in cache:
                    print(f"Checking latest version for {repo}...")
                    tag, sha = get_latest_tag_and_sha(repo)
                    cache[repo] = (tag, sha)
                    time.sleep(0.5) # Prevent rate limiting

                tag, sha = cache[repo]
                if sha and tag:
                    # Construct the new line: uses: owner/repo@<sha> # <tag>
                    new_uses = f"{prefix}{repo}@{sha} # {tag}"
                    # replace the whole uses part in the original line preserving indentation
                    new_line = line[:match.start()] + new_uses + '\n'
                    if new_line != line:
                        line = new_line
                        changed = True
            new_lines.append(line)

        if changed:
            with open(yml, 'w') as f:
                f.writelines(new_lines)
            updated_files += 1
            print(f"Updated {yml}")

    print(f"Finished updating {updated_files} workflow files.")

if __name__ == '__main__':
    update_workflows()
