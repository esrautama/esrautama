import re
with open("Index.html", "r") as f:
    text = f.read()

lines = text.split("\n")
for i, line in enumerate(lines):
    if "openFormProduct(" in line:
        print(f"Line {i+1}: {line.strip()}")
