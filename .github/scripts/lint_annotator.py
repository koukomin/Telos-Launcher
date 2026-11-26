import os
import glob
import xml.etree.ElementTree as ET

def annotate_lint():
    lint_files = glob.glob('**/build/reports/lint-results-*.xml', recursive=True)
    if not lint_files:
        print('No lint results found.')
        return

    cwd = os.getcwd()
    issue_count = 0

    for file in lint_files:
        try:
            tree = ET.parse(file)
            root = tree.getroot()
            for issue in root.findall('issue'):
                severity = issue.get('severity', 'Warning').lower()
                message = issue.get('message', '').replace('\n', ' ')
                issue_id = issue.get('id', 'Lint')

                # We only map 'error' and 'fatal' to github errors, others to warning
                gh_severity = 'error' if severity in ['error', 'fatal'] else 'warning'

                for location in issue.findall('location'):
                    filepath = location.get('file', '')
                    line = location.get('line', '1')
                    col = location.get('column', '1')

                    if filepath.startswith(cwd):
                        filepath = filepath[len(cwd)+1:]

                    # Format: ::warning file={file},line={line},col={col}::{message}
                    # Escaping colons or newlines if necessary
                    print(f"::{gh_severity} file={filepath},line={line},col={col}::[{issue_id}] {message}")
                    issue_count += 1
        except Exception as e:
            print(f'Failed to parse {file}: {e}')

    print(f'Annotated {issue_count} lint issues.')

if __name__ == '__main__':
    annotate_lint()
