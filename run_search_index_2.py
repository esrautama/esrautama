with open('Index.html', 'r', encoding='utf-8') as f:
    lines = f.readlines()

results = []
for idx in range(540, 600):
    if idx < len(lines):
        results.append(f"{idx+1}: {lines[idx]}")

with open('search_results_2.txt', 'w', encoding='utf-8') as f_out:
    f_out.writelines(results)
