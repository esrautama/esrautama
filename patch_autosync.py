def patch_file(file_name):
    with open(file_name, "r") as f:
        content = f.read()

    old_code = """                            state.adminProducts = res.products || [];
                            state.adminInjectOutlets = res.injectOutlets || [];
                            state.adminInjectStock = res.injectStock || [];
                            state.stokisStock = res.stokisStock || [];
                            state.rawData = res.rawData || [];"""
                            
    new_code = """                            if (res.users) state.adminUsers = res.users;
                            if (res.outlets) state.outlets = res.outlets;
                            if (res.stokisMaster) state.stokisMaster = res.stokisMaster;
                            state.adminProducts = res.products || [];
                            state.adminInjectOutlets = res.injectOutlets || [];
                            state.adminInjectStock = res.injectStock || [];
                            state.stokisStock = res.stokisStock || [];
                            state.rawData = res.rawData || [];"""

    if old_code in content:
        content = content.replace(old_code, new_code)
        with open(file_name, "w") as f:
            f.write(content)
        print(f"Patched {file_name}")
    else:
        print(f"Failed to find target in {file_name}")

patch_file("index_readable.html")
