import re

with open("Index.html", "r") as f:
    text = f.read()

# Replace escaped slashes
text = text.replace("\\/", "/")
# Check for any remaining \\"
text = text.replace("\\\"", "\"")

with open("Index.html", "w") as out:
    out.write(text)

