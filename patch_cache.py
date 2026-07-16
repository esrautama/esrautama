import re
def patch_file(file_name):
    with open(file_name, "r") as f:
        content = f.read()

    invalidation_code = """
function invalidateAllCaches() {
  try {
    const cache = CacheService.getScriptCache();
    cache.remove("getUsers");
    cache.remove("getProducts");
    cache.remove("getOutlets");
    const version = PropertiesService.getScriptProperties().getProperty("MASTER_DATA_VERSION") || "v1";
    cache.remove("getInitialData_" + version);
    cache.remove("getInitialData_" + version + "_");
    PropertiesService.getScriptProperties().setProperty("MASTER_DATA_VERSION", "v" + new Date().getTime());
  } catch(e) {}
}
"""

    if "function invalidateAllCaches()" not in content:
        content += invalidation_code

    # regex to find return { success: true, message: ... } and inject invalidateAllCaches();
    content = re.sub(r'(return \{ success: true, message:[^}]+\};)', r'invalidateAllCaches(); \1', content)

    with open(file_name, "w") as f:
        f.write(content)
    print(f"Patched {file_name}")

patch_file("AppsScript_Full.gs")
patch_file("AppsScript.gs")
