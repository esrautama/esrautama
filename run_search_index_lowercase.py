with open('index.html', 'r', encoding='utf-8') as f:
    lines = f.readlines()

for idx, line in enumerate(lines):
    if 'loginScreen' in line or 'login-screen' in line or 'id="login"' in line or 'id="login-form"' in line or 'class="login' in line:
        print(f"Line {idx+1}: {line.strip()}")
