with open("AppsScript_Full.gs", "r") as f:
    text = f.read()

import re

old_block = r"(function saveProduct.*?if \(!sheet\) \{.*?sheet\.appendRow\(\[\"ID\", \"Name\", \"Retail\", \"Wholesale\", \"Stock\"\]\);\s*\}\s*)(let lastRow = sheet\.getLastRow\(\);)"
new_code = r"""\1let lastRow = sheet.getLastRow();
    if (lastRow > 1) {
      let data = sheet.getRange(2, 1, lastRow - 1, 2).getValues();
      for(let i=0; i<data.length; i++) {
        if (id && String(data[i][0]) === String(id)) continue;
        if (String(data[i][1]).trim().toLowerCase() === String(name).trim().toLowerCase()) {
          return { success: false, message: "Gagal: Nama produk '" + name + "' sudah ada (duplikat)." };
        }
      }
    }
    """

if re.search(old_block, text, re.DOTALL):
    text = re.sub(old_block, new_code, text, flags=re.DOTALL)
    with open("AppsScript_Full.gs", "w") as f:
        f.write(text)
    with open("AppsScript.gs", "w") as f:
        f.write(text)
    print("Patched Products!")
else:
    print("Regex failed to match")
