import os

with open('Index.html', 'r', encoding='utf-8') as f:
    lines = f.readlines()

results = []
for idx, line in enumerate(lines):
    # Search for loginScreen area
    if idx >= 480 and idx <= 550:
        results.append(f"{idx+1}: {line}")
    elif 'fa-cog' in line or 'fa-gear' in line or 'fa-wrench' in line or 'fa-sliders' in line:
        results.append(f"ICON {idx+1}: {line}")

with open('search_results.txt', 'w', encoding='utf-8') as f_out:
    f_out.writelines(results)

print("Done writing results")
