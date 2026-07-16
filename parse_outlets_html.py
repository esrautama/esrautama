import re
with open("Index.html", "r") as f:
    text = f.read()

idx = text.find("Outlets Master</h3>")
start = max(0, idx - 100)
end = min(len(text), idx + 800)
print(text[start:end])
