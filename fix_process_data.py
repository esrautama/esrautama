import re
with open("Index.html", "r") as f:
    text = f.read()

target = """state.adminProducts = resDyn.products || [];"""
replacement = """state.adminProducts = resDyn.products || [];
                                        state.adminUsers = resDyn.users || state.adminUsers;
                                        state.outlets = resDyn.outlets || state.outlets;
                                        state.stokisMaster = resDyn.stokisMaster || state.stokisMaster;"""

if target in text:
    text = text.replace(target, replacement)
    with open("Index.html", "w") as f:
        f.write(text)
    print("Patched processDataLoad")
else:
    print("Target not found")
