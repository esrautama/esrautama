with open("AppsScript_Full.gs", "r") as f:
    text = f.read()

import re

old_catch = r'return ContentService\.createTextOutput\("Frontend tidak ditemukan\. Pastikan file Index\.html atau index\.html ada di project\."\);'
new_catch = r'return ContentService.createTextOutput("Gagal memuat frontend. Kemungkinan nama file bukan Index.html atau ada error di HTML. Error detail: " + err.message + " | " + err2.message);'

text = re.sub(old_catch, new_catch, text)

with open("AppsScript_Full.gs", "w") as f:
    f.write(text)
with open("AppsScript.gs", "w") as f:
    f.write(text)
print("Patched error message")
