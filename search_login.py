with open('Index.html', 'r', encoding='utf-8') as f:
    lines = f.readlines()

for idx, line in enumerate(lines):
    if 'loginScreen' in line or 'login-screen' in line or 'fa-cog' in line or 'fa-wrench' in line or 'fa-gear' in line:
        print(f"{idx+1}: {line.strip()}")
