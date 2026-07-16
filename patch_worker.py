with open("app/src/main/java/com/example/viewmodel/SyncWorker.kt", "r") as f:
    text = f.read()

import re

old_pull = r"""                // 2\. Pull Master Data\s*val lastMasterSync = prefs\.getString\(\"last_master_sync\", null\)\s*val initialData = service\.getInitialData\(lastSync = lastMasterSync\)"""

new_pull = r"""                // 2. Pull Master Data
                val lastMasterSync = prefs.getString("last_master_sync", null)
                val clientVersion = prefs.getString("master_data_version", null)
                val initialData = service.getInitialData(lastSync = lastMasterSync, clientVersion = clientVersion)
                
                if (initialData.version != null && initialData.version != clientVersion) {
                    prefs.edit().putString("master_data_version", initialData.version).apply()
                } else if (initialData.users.isEmpty() && initialData.products.isEmpty() && initialData.outlets.isEmpty()) {
                    // Version matches or no data returned
                    return@withContext Result.success()
                }"""

if re.search(old_pull, text):
    text = re.sub(old_pull, new_pull, text)
    with open("app/src/main/java/com/example/viewmodel/SyncWorker.kt", "w") as f:
        f.write(text)
    print("Patched SyncWorker version check")
else:
    print("Not found")
