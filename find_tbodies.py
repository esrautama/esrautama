import re
with open("Index.html", "r") as f:
    html = f.read()

tbodies = re.findall(r'<tbody id="([^"]+)"', html)
print(tbodies)
