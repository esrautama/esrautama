with open("Index.html", "r") as f:
    text = f.read()

import re
print("Has \\/:", "\\/" in text)
print("Has sandboxFrame:", "sandboxFrame" in text)
print("Has <\\/:", "<\\/" in text)
print("Length:", len(text))
print("Starts with:", text[:50])
print("Ends with:", text[-50:])
