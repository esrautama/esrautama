import sys

with open("app/src/main/java/com/example/ui/SfaApp.kt", "r") as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "val activeSales = currentUser?.id ?: \"\"" in line and "val salesAllocs = remember" in lines[i+1]:
        start = i
        for j in range(i, i+15):
            if "val allocatedProducts =" in lines[j]:
                for k in range(j, j+5):
                    if "}" in lines[k]:
                        end = k
                        break
                break
        
        extracted = lines[start:end+1]
        del lines[start:end+1]
        
        # find LazyColumn before this
        for j in range(start-1, start-20, -1):
            if "LazyColumn(" in lines[j]:
                lines = lines[:j] + extracted + lines[j:]
                with open("app/src/main/java/com/example/ui/SfaApp.kt", "w") as f:
                    f.writelines(lines)
                print("Fixed!")
                sys.exit(0)
