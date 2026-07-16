import sys

with open("app/src/main/java/com/example/ui/SfaApp.kt", "r") as f:
    lines = f.readlines()

# Fix the first one
for i, line in enumerate(lines):
    if "LazyColumn(" in line and "verticalArrangement = Arrangement.spacedBy(10.dp)," in lines[i+1]:
        start = i
        break

extracted = ""
to_delete = []
for i in range(start, start+20):
    if "val activeSales =" in lines[i]:
        to_delete.append(i)
        extracted += lines[i]
    if "val allocMap = remember" in lines[i]:
        open_b = 0
        in_alloc = True
        for j in range(i, i+10):
            open_b += lines[j].count('{')
            open_b -= lines[j].count('}')
            to_delete.append(j)
            extracted += lines[j]
            if open_b == 0 and "}" in lines[j]:
                break
    if "val allocatedProducts = remember" in lines[i]:
        open_b = 0
        in_alloc = True
        for j in range(i, i+10):
            open_b += lines[j].count('{')
            open_b -= lines[j].count('}')
            to_delete.append(j)
            extracted += lines[j]
            if open_b == 0 and "}" in lines[j]:
                break
        break

if extracted:
    for idx in reversed(to_delete):
        del lines[idx]
    lines.insert(start, extracted)

# Fix the second one
start2 = -1
for i, line in enumerate(lines):
    if "LazyColumn(" in line and "verticalArrangement = Arrangement.spacedBy(8.dp)," in lines[i+1]:
        start2 = i
        break

extracted2 = ""
to_delete2 = []
if start2 != -1:
    for i in range(start2, start2+20):
        if "val activeSales =" in lines[i]:
            to_delete2.append(i)
            extracted2 += lines[i]
        if "val salesAllocs = remember" in lines[i]:
            open_b = 0
            for j in range(i, i+10):
                open_b += lines[j].count('{')
                open_b -= lines[j].count('}')
                to_delete2.append(j)
                extracted2 += lines[j]
                if open_b == 0 and "}" in lines[j]:
                    break
        if "val allocMap = remember(salesAllocs)" in lines[i]:
            open_b = 0
            for j in range(i, i+10):
                open_b += lines[j].count('{')
                open_b -= lines[j].count('}')
                to_delete2.append(j)
                extracted2 += lines[j]
                if open_b == 0 and "}" in lines[j]:
                    break
        if "val allocatedProducts = remember" in lines[i]:
            open_b = 0
            for j in range(i, i+10):
                open_b += lines[j].count('{')
                open_b -= lines[j].count('}')
                to_delete2.append(j)
                extracted2 += lines[j]
                if open_b == 0 and "}" in lines[j]:
                    break
            break

    if extracted2:
        for idx in reversed(to_delete2):
            del lines[idx]
        lines.insert(start2, extracted2)

with open("app/src/main/java/com/example/ui/SfaApp.kt", "w") as f:
    f.writelines(lines)
    print("Fixed remember scopes!")
