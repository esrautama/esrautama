import re
with open("temp.js", "r") as f:
    js = f.read()

idx = js.find("function openFormOutlet")
if idx == -1:
    print("NOT FOUND")
else:
    start = max(0, idx - 100)
    end = min(len(js), idx + 2000)
    print(js[start:end])
