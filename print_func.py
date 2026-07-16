import re
with open("temp.js", "r") as f:
    text = f.read()

def print_func(name):
    m = re.search(r'function\s+' + name + r'\s*\([^)]*\)\s*\{', text)
    if m:
        start = m.start()
        # Find closing brace
        count = 1
        i = start + len(m.group(0))
        while i < len(text) and count > 0:
            if text[i] == '{': count += 1
            elif text[i] == '}': count -= 1
            i += 1
        print("==========", name)
        print(text[start:i])
        print("==========")
    else:
        print("NOT FOUND:", name)

print_func("openFormUser")
print_func("saveUserDb")
print_func("openFormProduct")
