import sys

with open('app/src/main/java/com/example/ui/SfaApp.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
skip = False
for line in lines:
    if 'AdminStokisSection(' in line and 'viewModel = viewModel' in line and 'onAddStokis = ' in line:
        new_lines.append('                            AdminStokisSection(\n')
        new_lines.append('                                viewModel = viewModel,\n')
        new_lines.append('                                onAddStokis = { onAddDataClick("Stokis") },\n')
        new_lines.append('                                onEditStokis = { onEditDataClick("Stokis", it.id) }\n')
        new_lines.append('                            )\n')
        skip = True
        continue
    if skip:
        if ')' in line and 'viewModel = viewModel' not in line:
            skip = False
        continue
    
    # We also need to add Stokis to AddEditDataDialog
    new_lines.append(line)

with open('app/src/main/java/com/example/ui/SfaApp.kt', 'w') as f:
    f.writelines(new_lines)

