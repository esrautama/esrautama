import re
with open("temp.js", "r") as f:
    js = f.read()

calls = re.findall(r'renderTable\([^)]*\);', js, re.DOTALL)
for call in calls:
    if "Stokis" in call or "stokis" in call:
        print(call.replace('\n', ' '))
