import sys

with open("app/src/main/java/com/example/ui/SfaApp.kt", "r") as f:
    text = f.read()

# fix AdminReceiptSettingsSection
old_admin = """                        try {
                            val imageBytes = android.util.Base64.decode(logoBase64, android.util.Base64.DEFAULT)
                            val decodedImage = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                            androidx.compose.foundation.Image(
                                bitmap = decodedImage.asImageBitmap(),
                                contentDescription = "Logo",
                                modifier = Modifier.size(80.dp)
                            )
                        } catch (e: Exception) {
                            Text("Invalid Image", color = Color.Red)
                        }"""
                        
new_admin = """                        val decodedBmp = try {
                            val imageBytes = android.util.Base64.decode(logoBase64, android.util.Base64.DEFAULT)
                            android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        } catch(e: Exception) { null }
                        
                        if (decodedBmp != null) {
                            androidx.compose.foundation.Image(
                                bitmap = decodedBmp.asImageBitmap(),
                                contentDescription = "Logo",
                                modifier = Modifier.size(80.dp)
                            )
                        } else {
                            Text("Invalid Image", color = Color.Red)
                        }"""

text = text.replace(old_admin, new_admin)

# fix ReceiptPreviewDialog
old_preview = """                                try {
                                    val imageBytes = android.util.Base64.decode(s.logoBase64, android.util.Base64.DEFAULT)
                                    val decodedImage = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                    
                                    val alignment = when(s.logoAlign) {
                                        "Left" -> Alignment.Start
                                        "Right" -> Alignment.End
                                        else -> Alignment.CenterHorizontally
                                    }
                                    
                                    androidx.compose.foundation.Image(
                                        bitmap = decodedImage.asImageBitmap(),
                                        contentDescription = "Logo",
                                        modifier = Modifier.align(alignment).padding(bottom = 8.dp).sizeIn(maxWidth = 150.dp, maxHeight = 100.dp),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                    )
                                } catch (e: Exception) {
                                    // ignore preview error
                                }"""

new_preview = """                                val decodedBmp = try {
                                    val imageBytes = android.util.Base64.decode(s.logoBase64, android.util.Base64.DEFAULT)
                                    android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                } catch(e: Exception) { null }
                                
                                if (decodedBmp != null) {
                                    val alignment = when(s.logoAlign) {
                                        "Left" -> Alignment.Start
                                        "Right" -> Alignment.End
                                        else -> Alignment.CenterHorizontally
                                    }
                                    
                                    androidx.compose.foundation.Image(
                                        bitmap = decodedBmp.asImageBitmap(),
                                        contentDescription = "Logo",
                                        modifier = Modifier.align(alignment).padding(bottom = 8.dp).sizeIn(maxWidth = 150.dp, maxHeight = 100.dp),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                    )
                                }"""

text = text.replace(old_preview, new_preview)

with open("app/src/main/java/com/example/ui/SfaApp.kt", "w") as f:
    f.write(text)
