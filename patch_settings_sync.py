with open("Index.html", "r") as f:
    text = f.read()

import re
sync_ui = """
                            <div class="relative border border-gray-300 rounded-lg p-4 pt-6 mt-4">
                                <span class="absolute -top-3 left-4 bg-white px-2 text-xs font-bold text-gray-500">Sinkronisasi Database</span>
                                <div class="flex flex-col gap-2">
                                    <p class="text-xs text-gray-500 mb-2">Jalankan proses Sinkronisasi Dua Arah (Two-Ways Sync) untuk memastikan data Admin dan Database Utama sinkron sepenuhnya.</p>
                                    <button onclick="processDataLoad()" class="w-full bg-blue-600 hover:bg-blue-700 text-white font-bold py-2.5 rounded-xl shadow-md transition text-sm flex items-center justify-center gap-2">
                                        <i class="fas fa-sync-alt"></i> Jalankan Two-Ways Sync
                                    </button>
                                </div>
                            </div>
"""

# Insert right before the "Simpan Pengaturan" button wrapper
target = '<div class="mt-8 flex justify-end">'
text = text.replace(target, sync_ui + target)

with open("Index.html", "w") as f:
    f.write(text)
