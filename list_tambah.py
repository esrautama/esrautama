import re
with open("Index.html", "r") as f:
    text = f.read()

# Find buttons
matches = re.finditer(r'<button[^>]*>.*?Tambah.*?</button>', text, re.DOTALL | re.IGNORECASE)
for m in matches:
    print(m.group(0)[:150])
