with open("Index.html", "r") as f:
    text = f.read()

import re
script_content = re.search(r'<script>(.*?)</script></body></html>', text, re.DOTALL)
if script_content:
    js = script_content.group(1)
    with open("temp.js", "w") as out:
        out.write(js)
    print("Extracted temp.js")
