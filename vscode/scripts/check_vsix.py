import zipfile
import sys
import hashlib

path = sys.argv[1]
expected_dll_path = sys.argv[2] if len(sys.argv) > 2 else None
z = zipfile.ZipFile(path)
names = z.namelist()
required = {
    'extension/bin/ime_sys.dll': any(n == 'extension/bin/ime_sys.dll' for n in names),
    'extension/node_modules/koffi/': any(n.startswith('extension/node_modules/koffi/') for n in names),
    'koffi win32 x64 native binary': any(
        n == 'extension/node_modules/@koromix/koffi-win32-x64/win32_x64/koffi.node'
        for n in names
    ),
}

missing = [label for label, ok in required.items() if not ok]
if missing:
    print('VSIX missing runtime files: ' + ', '.join(missing), file=sys.stderr)
    sys.exit(1)

forbidden_prefixes = (
    'extension/node_modules/@koromix/koffi-linux-',
    'extension/node_modules/@koromix/koffi-darwin-',
    'extension/node_modules/@koromix/koffi-freebsd-',
    'extension/node_modules/@koromix/koffi-openbsd-',
)
forbidden = [n for n in names if n.startswith(forbidden_prefixes)]
if forbidden:
    print('VSIX contains non-Windows koffi native files:', file=sys.stderr)
    for name in forbidden[:20]:
        print(f'  {name}', file=sys.stderr)
    if len(forbidden) > 20:
        print(f'  ... {len(forbidden) - 20} more', file=sys.stderr)
    sys.exit(1)

if expected_dll_path:
    with open(expected_dll_path, 'rb') as f:
        expected = hashlib.sha256(f.read()).hexdigest()
    actual = hashlib.sha256(z.read('extension/bin/ime_sys.dll')).hexdigest()
    if actual != expected:
        print(
            'VSIX ime_sys.dll hash mismatch: '
            f'expected {expected}, got {actual}',
            file=sys.stderr,
        )
        sys.exit(1)

print('VSIX runtime files OK')
