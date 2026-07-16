import re
with open("Index.html", "r") as f:
    text = f.read()

text = text.replace("><", ">\n<")
text = text.replace("\\n", "\n")
with open("Index_pretty.html", "w") as f:
    f.write(text)
