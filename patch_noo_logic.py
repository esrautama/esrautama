with open("AppsScript_Full.gs", "r") as f:
    text = f.read()

import re

old_block = r"let newId = \"NOO-\" \+ newIdNum;\s*sheet\.appendRow\(\[newId, name, address, \"Regular\", \"Retail\", geo, \"\", salesName, day\]\);"
new_code = r"""let newId = String(newIdNum).padStart(6, '0');
    let today = new Date();
    let dd = String(today.getDate()).padStart(2, '0');
    let mm = String(today.getMonth() + 1).padStart(2, '0');
    let keterangan = "NOO " + dd + "/" + mm;
    sheet.appendRow([newId, name, address, keterangan, "Retail", geo, "", salesName, day]);"""

if re.search(old_block, text):
    text = re.sub(old_block, new_code, text)
    with open("AppsScript_Full.gs", "w") as f:
        f.write(text)
    with open("AppsScript.gs", "w") as f:
        f.write(text)
    print("Patched NOO Logic")
else:
    print("Not found")
