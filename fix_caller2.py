import sys
with open('app/src/main/java/com/example/ui/SfaApp.kt', 'r') as f:
    content = f.read()

bad_str = """                            AdminStokisSection(
                                viewModel = viewModel,
                                onAddStokis = { onAddDataClick("Stokis") },
                                onEditStokis = { onEditDataClick("Stokis", it.id) }
                            )
                            //
                                viewModel = viewModel
                            )"""
good_str = """                            AdminStokisSection(
                                viewModel = viewModel,
                                onAddStokis = { onAddDataClick("Stokis") },
                                onEditStokis = { onEditDataClick("Stokis", it) }
                            )"""

content = content.replace(bad_str, good_str)

# Also fix the AddEditDataDialog where Stokis is not handled
with open('app/src/main/java/com/example/ui/SfaApp.kt', 'w') as f:
    f.write(content)
