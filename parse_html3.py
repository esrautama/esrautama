import re
with open("Index.html", "r") as f:
    html = f.read()

idx = html.find("Stokis Master")
start = max(0, idx - 100)
end = min(len(html), idx + 2000)
print(html[start:end])
