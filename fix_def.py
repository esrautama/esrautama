import sys

with open('app/src/main/java/com/example/ui/SfaApp.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
skip = False
for i, line in enumerate(lines):
    if line.strip() == '@Composable' and 'fun AdminStokisSection(' in lines[i+1]:
        new_lines.append('@Composable\n')
        new_lines.append('fun AdminStokisSection(\n')
        new_lines.append('    viewModel: SfaViewModel,\n')
        new_lines.append('    onAddStokis: () -> Unit,\n')
        new_lines.append('    onEditStokis: (com.example.data.StokisEntity) -> Unit\n')
        new_lines.append(') {\n')
        skip = True
        continue
    if skip:
        if line.strip() == ') {':
            skip = False
        continue
    new_lines.append(line)

with open('app/src/main/java/com/example/ui/SfaApp.kt', 'w') as f:
    f.writelines(new_lines)

