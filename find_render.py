with open("temp.js", "r") as f:
    js = f.read()

idx = js.find("state.adminProducts.forEach(p => {")
start = max(0, idx - 100)
end = min(len(js), idx + 800)
print(js[start:end])
