import re
with open("temp.js", "r") as f:
    js = f.read()

idx = js.find("function openFormStokis")
start = max(0, idx - 100)
end = min(len(js), idx + 2000)
print(js[start:end])
