import re
with open("Index.html", "r") as f:
    html = f.read()

# find openFormProduct(
idx = html.find("openFormProduct(")
start = max(0, idx - 100)
end = min(len(html), idx + 200)
print(html[start:end])
