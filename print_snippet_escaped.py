import html

with open('index_login_snippet.txt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

print(f"Total lines: {len(lines)}")
for line in lines[:40]:
    # Escape HTML characters so they don't get truncated or interpreted by the platform
    escaped = html.escape(line)
    print(escaped.strip())
