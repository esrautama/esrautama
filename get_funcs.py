import re
with open("temp.js", "r") as f:
    text = f.read()

funcs = re.findall(r'function\s+(\w+)\s*\(', text)
for i, func in enumerate(funcs):
    print(func)

