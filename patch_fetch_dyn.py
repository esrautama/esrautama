def patch_file(file_name):
    with open(file_name, "r") as f:
        content = f.read()

    old_code = """    const rawData = rawDataSheet ? SheetUtil.getDataAsObjects(rawDataSheet) : [];
    
    return {
      success: true,
      injectOutlets: injectOutlets,
      injectStock: injectStock,
      stokisStock: stokisStock,
      rawData: rawData
    };"""
    
    new_code = """    const rawData = rawDataSheet ? SheetUtil.getDataAsObjects(rawDataSheet) : [];
    
    const pSheet = ms.getSheet("Products");
    const products = pSheet ? SheetUtil.getDataAsObjects(pSheet) : [];
    
    return {
      success: true,
      injectOutlets: injectOutlets,
      injectStock: injectStock,
      stokisStock: stokisStock,
      rawData: rawData,
      products: products
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
