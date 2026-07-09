import zipfile
import sys

path = sys.argv[1]
z = zipfile.ZipFile(path)
names = z.namelist()
required = {
    'extension/bin/ime_sys.dll': any(n == 'extension/bin/ime_sys.dll' for n in names),
    'extension/node_modules/koffi/': any(n.startswith('extension/node_modules/koffi/') for n in names),
    'extension/node_modules/@koromix/': any(n.startswith('extension/node_modules/@koromix/') for n in names),
    'koffi native binary': any('koffi.node' in n for n in names),
}

missing = [label for label, ok in required.items() if not ok]
if missing:
    print('VSIX missing runtime files: ' + ', '.join(missing), file=sys.stderr)
    sys.exit(1)

print('VSIX runtime files OK')
