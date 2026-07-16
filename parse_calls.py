import re
with open("temp.js", "r") as f:
    js = f.read()

calls = re.findall(r'renderTable\(.*?\);', js, re.DOTALL)
for call in calls:
    print(call.replace('\n', ' '))
