import os
import glob
import xml.etree.ElementTree as ET

BASE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'core', 'i18n', 'src', 'main', 'res')
EN_PATH = os.path.join(BASE_DIR, 'values', 'strings_audit22_stremio.xml')
EL_PATH = os.path.join(BASE_DIR, 'values-el', 'strings_audit22_stremio.xml')

def parse_strings(path):
    if not os.path.exists(path):
        return {}
    tree = ET.parse(path)
    root = tree.getroot()
    result = {}
    for elem in root.findall('string'):
        name = elem.attrib.get('name')
        if name:
            result[name] = elem.text or ''
    return result

def main():
    en_strings = parse_strings(EN_PATH)
    el_strings = parse_strings(EL_PATH)

    values_dirs = glob.glob(os.path.join(BASE_DIR, 'values-*'))
    for d in values_dirs:
        target_path = os.path.join(d, 'strings_audit22_stremio.xml')
        if not os.path.exists(target_path):
            continue
        tree = ET.parse(target_path)
        root = tree.getroot()
        existing_names = {elem.attrib.get('name') for elem in root.findall('string')}

        modified = False
        for name, text in en_strings.items():
            if name not in existing_names:
                elem = ET.SubElement(root, 'string')
                elem.attrib['name'] = name
                # If values-el, use Greek text if available
                if 'values-el' in d and name in el_strings:
                    elem.text = el_strings[name]
                else:
                    elem.text = text
                modified = True

        if modified:
            # Write out formatted XML
            ET.indent(tree, space="    ")
            tree.write(target_path, encoding='utf-8', xml_declaration=True)
            print(f"Updated {target_path}")

if __name__ == '__main__':
    main()
