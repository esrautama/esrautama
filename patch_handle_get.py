with open("AppsScript_Full.gs", "r") as f:
    text = f.read()

import re

old_call = r"return ContentService\.createTextOutput\(new MasterService\(\)\.getInitialData\(e\.parameter\.lastSync\)\)\.setMimeType\(ContentService\.MimeType\.JSON\);"
new_call = r"return ContentService.createTextOutput(new MasterService().getInitialData(e.parameter.lastSync, e.parameter.clientVersion)).setMimeType(ContentService.MimeType.JSON);"

if re.search(old_call, text):
    text = re.sub(old_call, new_call, text)
    with open("AppsScript_Full.gs", "w") as f:
        f.write(text)
    with open("AppsScript.gs", "w") as f:
        f.write(text)
    print("Patched handleGet version check")
else:
    print("Not found")
