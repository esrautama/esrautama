with open('app/src/main/java/com/example/ui/SfaApp.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

results = []
for idx, line in enumerate(lines):
    if 'showUrlSettings' in line or 'LoginUrlSettingsDialog' in line or 'detectTapGestures(onLongPress' in line:
        results.append(f"{idx+1}: {line}")
    # Also search for standard Icons inside the Login screen or other places
    if 'Login' in line and 'fun ' in line:
        results.append(f"FUNCTION {idx+1}: {line}")

with open('search_kt_results.txt', 'w', encoding='utf-8') as f_out:
    f_out.writelines(results)

print("Done writing Kotlin results")
