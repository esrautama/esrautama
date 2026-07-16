with open('index.html', 'r', encoding='utf-8') as f:
    content = f.read()

import re
# Let's find any div that contains login in its id
matches = re.findall(r'<div[^>]*id=["\'][^"\']*login[^"\']*["\'][^>]*>', content, re.IGNORECASE)
for m in matches:
    print(f"Match: {m}")
