with open("AppsScript_Full.gs", "r") as f:
    text = f.read()

import re

old_block = r"return \{\s*success: true,\s*users: users,"
new_code = r"return { success: true, appName: SpreadsheetApp.getActiveSpreadsheet().getName(), users: users,"

if re.search(old_block, text):
    text = re.sub(old_block, new_code, text)
    with open("AppsScript_Full.gs", "w") as f:
        f.write(text)
    with open("AppsScript.gs", "w") as f:
        f.write(text)
    print("Patched appName!")
else:
    print("Regex failed to match")
