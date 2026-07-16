with open('app/src/main/java/com/example/ui/SfaApp.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

results = []
# Let's extract lines 650 to 1040
for idx in range(649, min(1040, len(lines))):
    results.append(f"{idx+1}: {lines[idx]}")

with open('login_screen_kt.txt', 'w', encoding='utf-8') as f_out:
    f_out.writelines(results)

print("Done writing LoginScreen Kotlin snippet")
