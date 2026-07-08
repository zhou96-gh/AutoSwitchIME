import zipfile
import sys

path = sys.argv[1]
z = zipfile.ZipFile(path)
for f in z.namelist():
    if 'koromix' in f or 'koffi.node' in f:
        print(f)
