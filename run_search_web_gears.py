with open('Index.html', 'r', encoding='utf-8') as f:
    content_index = f.read()

import re
matches_index = re.findall(r'[^<\n]*(?:cog|gear|settings)[^>\n]*', content_index, re.IGNORECASE)
print(f"Matches in Index.html ({len(matches_index)}):")
for m in matches_index[:15]:
    print(m.strip())

with open('index.html', 'r', encoding='utf-8') as f:
    content_lowercase = f.read()

matches_lowercase = re.findall(r'[^<\n]*(?:cog|gear|settings)[^>\n]*', content_lowercase, re.IGNORECASE)
print(f"\nMatches in index.html ({len(matches_lowercase)}):")
for m in matches_lowercase[:15]:
    print(m.strip())
