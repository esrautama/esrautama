import re

with open("AppsScript_Full.gs", "r") as f:
    text = f.read()

target = """sheet.getRange(i + 2, 2, 1, 3).setValues([[username, password, role]]);"""
replacement = """if (password === '••••••••') {
            // Only update username and role
            sheet.getRange(i + 2, 2, 1, 1).setValue(username);
            sheet.getRange(i + 2, 4, 1, 1).setValue(role);
          } else {
            sheet.getRange(i + 2, 2, 1, 3).setValues([[username, password, role]]);
          }"""

if target in text:
    text = text.replace(target, replacement)
    with open("AppsScript_Full.gs", "w") as f:
        f.write(text)
    print("Patched saveUser password handling")
else:
    print("Could not find target in AppsScript_Full.gs")

