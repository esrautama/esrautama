with open("Index.html", "r") as f:
    text = f.read()

import re

# In finishLoginRouting
text = text.replace(
    'if (\n          state.currentUser.role === "Admin" ||\n          state.currentUser.role === "Super Admin"\n        ) {',
    'const userRole = String(state.currentUser.role).toLowerCase();\n        if (\n          userRole === "admin" ||\n          userRole === "super admin"\n        ) {'
)

# In switchTab
text = text.replace(
    'if (state.currentUser && state.currentUser.role === "Sales") {',
    'const userRole = state.currentUser ? String(state.currentUser.role).toLowerCase() : "";\n        if (userRole === "sales") {'
)
text = text.replace(
    'else if (\n          state.currentUser &&\n          (state.currentUser.role === "Admin" ||\n            state.currentUser.role === "Super Admin")\n        )',
    'else if (\n          userRole === "admin" ||\n          userRole === "super admin"\n        )'
)

with open("Index.html", "w") as f:
    f.write(text)
