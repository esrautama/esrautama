with open("index_readable.html", "r") as f:
    text = f.read()

import re

# Fix routing check
old_routing = "if(state.currentUser.role === 'Admin') {"
new_routing = "if(state.currentUser.role === 'Admin' || state.currentUser.role === 'Super Admin') {"
text = text.replace(old_routing, new_routing)

# Fix switchTab check
old_switch = "} else if (state.currentUser && state.currentUser.role === 'Admin') {"
new_switch = "} else if (state.currentUser && (state.currentUser.role === 'Admin' || state.currentUser.role === 'Super Admin')) {"
text = text.replace(old_switch, new_switch)

with open("index_readable.html", "w") as f:
    f.write(text)
print("Patched Roles")
