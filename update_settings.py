import sys

with open("app/src/main/java/com/example/ui/SfaApp.kt", "r") as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "fun AdminReceiptSettingsSection(" in line:
        insert_idx = i - 1
        break

# check if we need to add imports
with open("app/src/main/java/com/example/ui/SfaApp.kt", "w") as f:
    f.writelines(lines)
