import sys

with open("app/src/main/java/com/example/data/SyncService.kt", "r") as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "suspend fun checkHealth" in line:
        # extract lines 41, 42
        check_health_lines = lines[i-1:i+1]
        del lines[i-1:i+1]
        # insert before the closing brace of interface
        for j in range(len(lines)):
            if "suspend fun syncTransactions(" in lines[j]:
                lines.insert(j+1, check_health_lines[0])
                lines.insert(j+2, check_health_lines[1])
                break
        break

with open("app/src/main/java/com/example/data/SyncService.kt", "w") as f:
    f.writelines(lines)
