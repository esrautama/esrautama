import json

with open("index_readable.html", "r") as f:
    text = f.read()

idx = text.find('"userHtml":"')
if idx != -1:
    start = idx + len('"userHtml":"')
    end = text.find('","ncc":', start)
    escaped = text[start:end]
    
    # escaped has things like \x3c. JSON requires \u003c.
    # Python's codecs can do unicode escape
    import codecs
    real_html = codecs.decode(escaped, 'unicode_escape')
    with open("Index.html", "w") as out:
        out.write(real_html)
    with open("index.html", "w") as out:
        out.write(real_html)
    print("Extracted via codecs! Length:", len(real_html))
