with open("index_readable.html", "r") as f:
    text = f.read()

# find "userHtml":"
idx = text.find('"userHtml":"')
if idx != -1:
    start = idx + len('"userHtml":"')
    # find ","ncc":
    end = text.find('","ncc":', start)
    if end != -1:
        escaped = text[start:end]
        import ast
        # escaped string is something like \x3c!DOCTYPE html>\n...
        # Let's wrap it in quotes and parse
        try:
            real_html = ast.literal_eval('"' + escaped + '"')
            with open("Index.html", "w") as out:
                out.write(real_html)
            with open("index.html", "w") as out:
                out.write(real_html)
            print("Extracted via AST!")
        except Exception as e:
            print("AST error:", e)
    else:
        print("End not found")
else:
    print("userHtml not found")
