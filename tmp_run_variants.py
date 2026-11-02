import subprocess
ps_exe = r"C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe"
parser = r"C:\Users\Win11 Pro\Documents\samsung-akg-eq\tmp_parse_test.ps1"
path = r"scripts/windows/setup-sonora.ps1"
with open(path, encoding='utf-8') as f:
    text = f.read()

variants = {
    "bom_only": "\ufeff" + text,
    "bom_emdash_replaced": "\ufeff" + text.replace("\u2014", "--").replace("\u00fc", "u").replace("\u00f6", "o"),
}

for name, content in variants.items():
    tmp = f"/tmp/variant_{name}.ps1"
    with open(tmp, "w", encoding="utf-8") as tf:
        tf.write(content)
    out = f"/tmp/out_{name}.txt"
    r = subprocess.run([ps_exe, "-NoProfile", "-File", parser, tmp, out],
                       stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    try:
        with open(out, encoding="utf-8") as tf:
            content_out = tf.read()
    except FileNotFoundError:
        content_out = "(no output file)"
    print(f"=== {name} ===")
    print(content_out)
    print()
