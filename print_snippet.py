with open('index_login_snippet.txt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

print(f"Number of lines: {len(lines)}")
for idx, line in enumerate(lines[:40]):
    print(f"{idx+1}: {line.strip()}")
