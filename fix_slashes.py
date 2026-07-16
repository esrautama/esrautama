with open("Index.html", "r") as f:
    text = f.read()

text = text.replace("<\\/", "</")
text = text.replace("\\\"", "\"")
# It's probably mostly correct now. Let's write it to index_clean.html
with open("index_clean.html", "w") as out:
    out.write(text)
