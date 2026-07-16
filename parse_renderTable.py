with open("temp.js", "r") as f:
    js = f.read()

idx = js.find("function renderTable")
if idx != -1:
    print(js[idx:idx+200])

