import sys

with open("app/src/main/java/com/example/ui/SfaApp.kt", "r") as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "val sizeMb = db.sizeBytes?.let { it / 1024.0 / 1024.0 } ?: 0.0" in line:
        lines[i] = "            val sizeMb = db.sizeBytes?.toDouble()?.let { it / 1024.0 / 1024.0 } ?: 0.0\n"
        break

with open("app/src/main/java/com/example/ui/SfaApp.kt", "w") as f:
    f.writelines(lines)
