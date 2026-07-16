def patch_file(file_name):
    with open(file_name, "r") as f:
        content = f.read()

    old_code = """    const sSheet = ms.getSheet("Stokis");
    const stokisMaster = sSheet ? SheetUtil.getDataAsObjects(sSheet) : [];
    
    return {
      success: true,
      users: users,
      products: products,
      stokisMaster: stokisMaster
    };"""
    
    new_code = """    const sSheet = ms.getSheet("Stokis");
    const stokisMaster = sSheet ? SheetUtil.getDataAsObjects(sSheet) : [];
    
    const oSheet = ms.getSheet("Outlets");
    const outlets = oSheet ? SheetUtil.getDataAsObjects(oSheet) : [];
    
    return {
      success: true,
      users: users,
      products: products,
      stokisMaster: stokisMaster,
      outlets: outlets
    };"""

    if old_code in content:
        content = content.replace(old_code, new_code)
        with open(file_name, "w") as f:
            f.write(content)
        print(f"Patched {file_name}")
    else:
        print(f"Failed to find target in {file_name}")

patch_file("AppsScript_Full.gs")
patch_file("AppsScript.gs")
