with open("Index.html", "r") as f:
    text = f.read()

# some literal <\b or something might be there but it's ok.
# It ends with </body></html> now.
