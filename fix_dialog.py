import sys

with open('app/src/main/java/com/example/ui/SfaApp.kt', 'r') as f:
    content = f.read()

# Fix AdminStokisSection call
content = content.replace('onEditStokis = { onEditDataClick("Stokis", it) }', 'onEditStokis = { onEditDataClick("Stokis", allStokis.indexOf(it)) }')

# Add Stokis to AddEditDataDialog fields
content = content.replace('val outlets by viewModel.outletsList.collectAsStateWithLifecycle()', 'val outlets by viewModel.outletsList.collectAsStateWithLifecycle()\n    val allStokis by viewModel.stokisList.collectAsStateWithLifecycle()')

# We need to inject logic into AddEditDataDialog LaunchedEffect
launch_eff_old = """        if (editIndex != null) {
            when (dataType) {
                "Users" -> {
"""
launch_eff_new = """        if (editIndex != null) {
            when (dataType) {
                "Stokis" -> {
                    val st = allStokis.getOrNull(editIndex)
                    if (st != null) {
                        fId = st.id
                        fName = st.name
                        fAddr = st.address
                    }
                }
                "Users" -> {
"""
content = content.replace(launch_eff_old, launch_eff_new)

# Inject logic to rendering fields
render_old = """                    "OutletMain" -> {
                        OutlinedTextField(
"""
render_new = """                    "Stokis" -> {
                        OutlinedTextField(value = fId, onValueChange = { fId = it }, label = { Text("ID Stokis") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = fName, onValueChange = { fName = it }, label = { Text("Nama Stokis") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = fAddr, onValueChange = { fAddr = it }, label = { Text("Alamat") }, modifier = Modifier.fillMaxWidth())
                    }
                    "OutletMain" -> {
                        OutlinedTextField(
"""
content = content.replace(render_old, render_new)

# Inject logic to save action
save_old = """                        when (dataType) {
                            "Users" -> {
"""
save_new = """                        when (dataType) {
                            "Stokis" -> {
                                viewModel.saveStokis(com.example.data.StokisEntity(fId, fName, fAddr))
                            }
                            "Users" -> {
"""
content = content.replace(save_old, save_new)

with open('app/src/main/java/com/example/ui/SfaApp.kt', 'w') as f:
    f.write(content)
