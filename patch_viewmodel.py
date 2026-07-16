with open("app/src/main/java/com/example/viewmodel/SfaViewModel.kt", "r") as f:
    text = f.read()

import re

old_pull = r"""                    val lastMasterSync = sharedPrefs\.getString\(\"last_master_sync\", null\)\s*val initialData = service\.getInitialData\(lastSync = lastMasterSync\)"""

new_pull = r"""                    val lastMasterSync = sharedPrefs.getString("last_master_sync", null)
                    val clientVersion = sharedPrefs.getString("master_data_version", null)
                    val initialData = service.getInitialData(lastSync = lastMasterSync, clientVersion = clientVersion)
                    
                    if (initialData.version != null && initialData.version != clientVersion) {
                        sharedPrefs.edit().putString("master_data_version", initialData.version).apply()
                    } else if (initialData.users.isEmpty() && initialData.products.isEmpty() && initialData.outlets.isEmpty()) {
                        // Version matches or no data returned
                        return@withLock Pair(true, "Sinkronisasi berhasil (Data sudah yang terbaru)")
                    }"""

if re.search(old_pull, text):
    text = re.sub(old_pull, new_pull, text)
    with open("app/src/main/java/com/example/viewmodel/SfaViewModel.kt", "w") as f:
        f.write(text)
    print("Patched SfaViewModel version check")
else:
    print("Not found")
