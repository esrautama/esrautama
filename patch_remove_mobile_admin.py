with open("index_readable.html", "r") as f:
    text = f.read()

import re

mobile_admin_regex = r"\s*/\* --- MOBILE ADMIN STYLES --- \*/.*?@media \(min-width: 768px\) \{"
text = re.sub(mobile_admin_regex, "\n        @media (min-width: 768px) {", text, flags=re.DOTALL)

with open("index_readable.html", "w") as f:
    f.write(text)
print("Removed Mobile Admin CSS")
