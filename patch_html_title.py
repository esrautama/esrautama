with open("index_readable.html", "r") as f:
    text = f.read()

import re

old_block = r"state\.adminUsers = resStatic\.users \|\| \[\];"
new_code = r"""if (resStatic.appName) {
                                document.title = resStatic.appName;
                                document.querySelectorAll('.app-title-dynamic').forEach(el => el.innerText = resStatic.appName);
                            }
                            state.adminUsers = resStatic.users || [];"""

if re.search(old_block, text):
    text = re.sub(old_block, new_code, text)
    
    # We also need to add the class app-title-dynamic to the headers in the HTML
    # Like <h1 class="text-xl font-black tracking-tight drop-shadow-md">ESRA UTAMA SFA</h1>
    text = re.sub(
        r"ESRA UTAMA SFA",
        r"<span class=\"app-title-dynamic\">ESRA UTAMA SFA</span>",
        text
    )
    
    # Also for `<title>ESRA UTAMA SFA</title>` -> Wait, the replace above replaces it too.
    # It will become `<title><span class="app-title-dynamic">ESRA UTAMA SFA</span></title>`, which is invalid HTML for title.
    # So we need to fix the title tag.
    text = text.replace("<title><span class=\"app-title-dynamic\">ESRA UTAMA SFA</span></title>", "<title>ESRA UTAMA SFA</title>")
    
    with open("index_readable.html", "w") as f:
        f.write(text)
    print("Patched HTML title!")
else:
    print("Regex failed to match")
