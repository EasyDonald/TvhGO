import io, os

RES = r"C:\Users\Administrator\Documents\deepseek-harness\default-workspace\tvhclient\android\res\drawable"
os.makedirs(RES, exist_ok=True)

def vec(name, path, color="#9AA0A6", size=24):
    xml = '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="%ddp"
    android:height="%ddp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="%s"
        android:pathData="%s" />
</vector>
''' % (size, size, color, path)
    io.open(os.path.join(RES, name), "w", encoding="utf-8", newline="").write(xml)
    print("wrote", name)

vec("ic_refresh.xml",
    "M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.42,0 -7.99,3.58 -7.99,8s3.57,8 7.99,8c3.73,0 6.84,-2.55 7.73,-6h-2.08c-0.82,2.33 -3.04,4 -5.65,4 -3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6c1.66,0 3.14,0.69 4.22,1.78L13,11h7V4l-2.35,2.35z")

vec("ic_tune.xml",
    "M3,17v2h6v-2H3zM3,5v2h10V5H3zM13,21v-2h8v-2h-8v-2h-2v6h2zM7,9v2H3v2h4v2h2V9H7zM21,13v-2H11v2h10zM15,9h2V7h4V5h-4V3h-2v6z")

vec("ic_link.xml",
    "M3.9,12c0,-1.71 1.39,-3.1 3.1,-3.1h4V7H7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5h4v-1.9H7c-1.71,0 -3.1,-1.39 -3.1,-3.1zM8,13h8v-2H8v2zM17,7h-4v1.9h4c1.71,0 3.1,1.39 3.1,3.1s-1.39,3.1 -3.1,3.1h-4V17h4c2.76,0 5,-2.24 5,-5s-2.24,-5 -5,-5z")

vec("ic_play.xml", "M8,5v14l11,-7z", color="#101214")

vec("ic_back.xml",
    "M15.41,7.41L14,6l-6,6 6,6 1.41,-1.41L10.83,12z")
