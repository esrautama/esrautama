import re
with open("temp.js", "r") as f:
    text = f.read()

def print_func(name):
    m = re.search(r'function\s+' + name + r'\s*\([^)]*\)\s*\{', text)
    if m:
        print("FOUND:", name)
    else:
        print("NOT FOUND:", name)

print_func("openFormStokis")
print_func("saveStokisDb")
print_func("deleteStokisDb")
