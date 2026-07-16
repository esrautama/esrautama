with open("Index.html", "r") as f:
    text = f.read()

text = text.replace('String(state.currentUser.role).toLowerCase()', 'String(state.currentUser.role).toLowerCase().trim()')

with open("Index.html", "w") as f:
    f.write(text)
