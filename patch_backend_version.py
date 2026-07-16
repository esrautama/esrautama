with open("AppsScript_Full.gs", "r") as f:
    text = f.read()

import re

old_func = r"getInitialData\(lastSyncStr\)\s*\{\s*const version = PropertiesService\.getScriptProperties\(\)\.getProperty\(\"MASTER_DATA_VERSION\"\) \|\| \"v1\";\s*const cacheKey = \"getInitialData_\" \+ version \+ \(lastSyncStr \? \"_\" \+ lastSyncStr\.substring\(0,10\) : \"\"\);"

new_func = r"""getInitialData(lastSyncStr, clientVersion) {
    const version = PropertiesService.getScriptProperties().getProperty("MASTER_DATA_VERSION") || "v1";
    if (clientVersion && clientVersion === version) {
        return JSON.stringify({ version: version }); // version match, no need to send data
    }
    const cacheKey = "getInitialData_" + version + "_" + (lastSyncStr ? lastSyncStr.substring(0,10) : "all");"""

if re.search(old_func, text):
    text = re.sub(old_func, new_func, text)
    with open("AppsScript_Full.gs", "w") as f:
        f.write(text)
    with open("AppsScript.gs", "w") as f:
        f.write(text)
    print("Patched getInitialData version logic")
else:
    print("Not found")
