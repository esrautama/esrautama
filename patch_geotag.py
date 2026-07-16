def patch_file(file_name):
    with open(file_name, "r") as f:
        content = f.read()
        
    old = """navigator.geolocation.getCurrentPosition(function(position) {
                    var lat = position.coords.latitude.toFixed(5);
                    var lon = position.coords.longitude.toFixed(5);
                    var geo = lat + ", " + lon;
                    document.getElementById('nooGeotagVal').value = geo;
                    var btn = document.getElementById('btnGeotag');
                    btn.innerHTML = '<i class="fas fa-check text-green-500 mr-1"></i> Lokasi Tersimpan (' + geo + ')';
                    btn.classList.replace('text-red-600', 'text-green-600');
                }, function(error) {
                    document.getElementById('btnGeotag').innerHTML = '<i class="fas fa-exclamation-triangle mr-1"></i> Gagal ambil lokasi. Coba lagi.';
                    showMessage('Error Geotag', 'Pastikan izin lokasi (GPS) diaktifkan di browser/HP Anda.');
                });"""
                
    new_code = """navigator.geolocation.getCurrentPosition(function(position) {
                    var lat = position.coords.latitude.toFixed(6);
                    var lon = position.coords.longitude.toFixed(6);
                    var geo = lat + "," + lon;
                    document.getElementById('nooGeotagVal').value = geo;
                    var btn = document.getElementById('btnGeotag');
                    btn.innerHTML = '<i class="fas fa-check text-green-500 mr-1"></i> Lokasi Tersimpan (' + geo + ')';
                    btn.classList.replace('text-red-600', 'text-green-600');
                    fetch(`https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lon}`)
                        .then(response => response.json())
                        .then(data => {
                            if (data && data.display_name) {
                                var addrInput = document.getElementById('nooAddress');
                                if (!addrInput.value || addrInput.value.trim() === '') addrInput.value = data.display_name;
                                else addrInput.value = addrInput.value + ", " + data.display_name;
                            }
                        }).catch(e => console.log("Geocode err", e));
                }, function(error) {
                    document.getElementById('btnGeotag').innerHTML = '<i class="fas fa-exclamation-triangle mr-1"></i> Gagal ambil lokasi.';
                    showMessage('Error Geotag', 'Pastikan GPS aktif.');
                }, { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 });"""
                
    if old in content:
        content = content.replace(old, new_code)
        with open(file_name, "w") as f:
            f.write(content)
        print(f"Patched {file_name}")
    else:
        print("Not found")
        
patch_file("index_readable.html")
