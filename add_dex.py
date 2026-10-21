import zipfile, shutil, os

ROOT = os.path.dirname(os.path.abspath(__file__))
apk = os.path.join(ROOT, "build", "app-unsigned.apk")
dex = os.path.join(ROOT, "build", "dex", "classes.dex")

if not os.path.exists(dex):
    raise SystemExit("classes.dex missing: " + dex)

tmp = apk + ".tmp"
with zipfile.ZipFile(apk) as zin, zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        if item.filename == "classes.dex":
            continue
        zout.writestr(item, zin.read(item.filename))
    zout.write(dex, "classes.dex")

shutil.move(tmp, apk)
with zipfile.ZipFile(apk) as z:
    names = z.namelist()
print("dex inserted; entries:", len(names), "has classes.dex:", "classes.dex" in names)
