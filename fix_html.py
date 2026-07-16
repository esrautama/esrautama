import bs4
with open("Index.html", "r") as f:
    text = f.read()
soup = bs4.BeautifulSoup(text, "html.parser")
with open("Index_pretty.html", "w") as f:
    f.write(soup.prettify())
