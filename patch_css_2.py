with open("index_readable.html", "r") as f:
    text = f.read()

import re

old_css = """        body.admin-desktop #mainNav {
            overflow-x: auto;
            justify-content: flex-start;
            flex-wrap: nowrap;
        }
        body.admin-desktop .admin-logo-area { display: none !important; }"""

new_css = """        body.admin-desktop #mainNav {
            overflow-x: auto;
            justify-content: flex-start;
            flex-wrap: nowrap;
            padding-top: 5px;
            padding-bottom: 5px;
        }
        body.admin-desktop .admin-logo-area { display: none !important; }
        body.admin-desktop div.admin-only { display: none !important; } /* Hide group titles */"""

text = text.replace(old_css, new_css)

with open("index_readable.html", "w") as f:
    f.write(text)
print("Patched CSS 2")
