with open("app/src/main/java/com/example/ui/SfaApp.kt", "r") as f:
    lines = f.readlines()
start = -1
for i, line in enumerate(lines):
    if "fun AdminInjectStockSection(" in line:
        start = i
        break
if start != -1:
    for i in range(start, start + 130):
        if i < len(lines):
            print(lines[i], end="")
