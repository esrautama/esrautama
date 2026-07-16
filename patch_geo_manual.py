with open("index_readable.html", "r") as f:
    text = f.read()

old_block = """                navigator.geolocation.getCurrentPosition(function(position) {\\
                    var lat = position.coords.latitude.toFixed(5);\\
                    var lon = position.coords.longitude.toFixed(5);\\
                    var geo = lat + ", " + lon;\\
                    document.getElementById('nooGeotagVal').value = geo;\\
                    var btn = document.getElementById('btnGeotag');\\
                    btn.innerHTML = '<i class="fas fa-check text-green-500 mr-1"></i> Lokasi Tersimpan (' + geo + ')';\\
                    btn.classList.replace('text-red-600', 'text-green-600');\\
                }, function(error) {\\
                    document.getElementById('btnGeotag').innerHTML = '<i class="fas fa-exclamation-triangle mr-1"></i> Gagal ambil lokasi. Coba lagi.';\\
                    showMessage('Error Geotag', 'Pastikan izin lokasi (GPS) diaktifkan di browser/HP Anda.');\\
                });\\"""

new_block = """                navigator.geolocation.getCurrentPosition(function(position) {\\
                    var lat = position.coords.latitude.toFixed(5);\\
                    var lon = position.coords.longitude.toFixed(5);\\
                    var geo = lat + ", " + lon;\\
                    document.getElementById('nooGeotagVal').value = geo;\\
                    var btn = document.getElementById('btnGeotag');\\
                    btn.innerHTML = '<i class="fas fa-check text-green-500 mr-1"></i> Lokasi Tersimpan (' + geo + ')';\\
                    btn.classList.replace('text-red-600', 'text-green-600');\\
                    fetch(`https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lon}`).then(r=>r.json()).then(d=>{if(d&&d.display_name){var a=document.getElementById('nooAddress');a.value=a.value?a.value+', '+d.display_name:d.display_name;}}).catch(e=>console.log(e));\\
                }, function(error) {\\
                    document.getElementById('btnGeotag').innerHTML = '<i class="fas fa-exclamation-triangle mr-1"></i> Gagal ambil lokasi. Coba lagi.';\\
                    showMessage('Error Geotag', 'Pastikan izin lokasi (GPS) diaktifkan di browser/HP Anda.');\\
                }, {enableHighAccuracy: true, timeout: 10000, maximumAge: 0});\\"""

if old_block in text:
    text = text.replace(old_block, new_block)
    with open("index_readable.html", "w") as f:
        f.write(text)
    print("Replaced")
else:
    print("Not found")
