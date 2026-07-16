with open('app/src/main/java/com/example/ui/SfaApp.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

results = []
for idx in range(649, 890):
    if idx < len(lines):
        results.append(f"{idx+1}: {lines[idx]}")

with open('login_screen_part1.txt', 'w', encoding='utf-8') as f_out:
    f_out.writelines(results)

print("Done part 1")
