with open("temp.js", "r") as f:
    js = f.read()

idx = js.find("state.adminUsers.forEach")
if idx != -1:
    print("Found adminUsers loop at", idx)
    print(js[idx:idx+500])

idx = js.find("state.adminProducts.forEach")
if idx != -1:
    print("Found adminProducts loop at", idx)
    print(js[idx:idx+500])

idx = js.find("state.outlets.forEach")
if idx != -1:
    print("Found outlets loop at", idx)
    print(js[idx:idx+500])

