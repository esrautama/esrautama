import os

with open('index.html', 'r', encoding='utf-8') as f:
    content = f.read()

# Let's locate loginScreen in index.html and print it
import re
match = re.search(r'id=["\']loginScreen["\'].*?>(.*?)</div>\s*</div>', content, re.DOTALL)
if match:
    print("Found loginScreen in index.html:")
    print(match.group(1).strip()[:1500]) # Print first 1500 chars of the inner HTML of loginScreen
else:
    # Let's search just for loginScreen keyword in index.html
    for idx, line in enumerate(content.splitlines()):
        if 'loginScreen' in line:
            print(f"Line {idx+1}: {line}")
