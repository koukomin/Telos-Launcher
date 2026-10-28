import re

def update_file(path):
    with open(path, 'r') as f:
        code = f.read()

    if 'ndkVersion' not in code:
        code = code.replace("defaultConfig {", 'ndkVersion = "27.0.12077973"\n    defaultConfig {')
        with open(path, 'w') as f:
            f.write(code)

update_file('app/app/build.gradle.kts')
update_file('services/comms/build.gradle.kts')

