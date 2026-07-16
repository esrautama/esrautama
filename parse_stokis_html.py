import re
with open("Index.html", "r") as f:
    text = f.read()

idx = text.find("Stokis Master</h3>")
if idx == -1:
    idx = text.lower().find("stokis master")
start = max(0, idx - 100)
end = min(len(text), idx + 800)
print(text[start:end])
