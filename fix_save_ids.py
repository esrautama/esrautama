def patch_file(file_name):
    with open(file_name, "r") as f:
        content = f.read()

    old_user = """function saveUser(id, username, role, password) {
  try {
    const ms = new MasterService();"""
    
    new_user = """function saveUser(id, username, role, password) {
  try {
    if (!id || id.trim() === '') id = "USR-" + new Date().getTime();
    const ms = new MasterService();"""
    
    old_stokis = """function saveMasterStokis(id, name, address, sales) {
  try {
    const ms = new MasterService();"""
    
    new_stokis = """function saveMasterStokis(id, name, address, sales) {
  try {
    if (!id || id.trim() === '') id = "STK-" + new Date().getTime();
    const ms = new MasterService();"""

    if old_user in content: content = content.replace(old_user, new_user)
    if old_stokis in content: content = content.replace(old_stokis, new_stokis)
    
    with open(file_name, "w") as f:
        f.write(content)
    print(f"Patched {file_name}")

patch_file("AppsScript_Full.gs")
patch_file("AppsScript.gs")
