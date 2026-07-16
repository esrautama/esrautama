import re
with open("Index.html", "r") as f:
    html = f.read()

# Find the admin products template
pattern = r'<td class="px-2 py-3">.*?openFormProduct.*?</td>'
matches = re.findall(pattern, html, re.DOTALL)
if matches:
    print("Found openFormProduct matches:")
    for m in matches:
        print(m)
else:
    print("No matches")
