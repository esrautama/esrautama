import re
with open("temp.js", "r") as f:
    text = f.read()

m = re.search(r'function openFormModal\([^)]*\)\s*\{', text)
if m:
    start = m.start()
    count = 1
    i = start + len(m.group(0))
    while i < len(text) and count > 0:
        if text[i] == '{': count += 1
        elif text[i] == '}': count -= 1
        i += 1
    print(text[start:i])
