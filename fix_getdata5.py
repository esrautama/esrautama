def patch_file(file_name):
    with open(file_name, "r") as f:
        content = f.read()

    old_code = """        if (cleanKey === 'address') { obj['Address'] = row[j]; obj['address'] = row[j]; }"""
    new_code = """        if (cleanKey === 'address') { obj['Address'] = row[j]; obj['address'] = row[j]; }
        if (cleanKey === 'type') { obj['Type'] = row[j]; obj['type'] = row[j]; }
        if (cleanKey === 'geotag' || cleanKey === 'geotaglocation') { obj['Geotag'] = row[j]; obj['geotag'] = row[j]; }"""

    if old_code in content:
        content = content.replace(old_code, new_code)
        with open(file_name, "w") as f:
            f.write(content)
        print(f"Patched {file_name}")
    else:
        print(f"Failed to find target in {file_name}")

patch_file("AppsScript_Full.gs")
patch_file("AppsScript.gs")
