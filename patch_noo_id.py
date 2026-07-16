with open("AppsScript_Full.gs", "r") as f:
    text = f.read()

import re
text = re.sub(r"let newId = newIdNum\.toString\(\);", r'let newId = "NOO-" + newIdNum;', text)

with open("AppsScript_Full.gs", "w") as f:
    f.write(text)
with open("AppsScript.gs", "w") as f:
    f.write(text)
print("Fixed NOO ID")
