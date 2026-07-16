import sys

with open("app/src/main/java/com/example/ui/SfaApp.kt", "r") as f:
    lines = f.readlines()

# delete 1568-1569
del lines[1568:1570]

# insert closing brace at 1564 before LazyColumn
lines.insert(1564 - 1, "            }\n")

with open("app/src/main/java/com/example/ui/SfaApp.kt", "w") as f:
    f.writelines(lines)
