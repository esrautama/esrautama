import html

with open('index_login_snippet_2.txt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

print(f"Total lines: {len(lines)}")
for line in lines:
    print(html.escape(line).strip())
