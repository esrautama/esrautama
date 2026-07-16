import sys

with open("app/src/main/java/com/example/ui/SfaApp.kt", "r") as f:
    lines = f.readlines()

new_imports = """
import androidx.compose.ui.graphics.asImageBitmap
"""

for i, line in enumerate(lines):
    if "import androidx.compose.material.icons.filled.HealthAndSafety" in line:
        lines.insert(i, new_imports)
        break

# fix ReceiptPreviewDialog
for i, line in enumerate(lines):
    if "fun ReceiptPreviewDialog(" in line:
        if "@Composable" not in lines[i-1]:
            lines.insert(i, "@Composable\n")
        break

# fix AdminReceiptSettingsSection
for i, line in enumerate(lines):
    if "fun AdminReceiptSettingsSection(" in line:
        if "@Composable" not in lines[i-1]:
            lines.insert(i, "@Composable\n")
        break

with open("app/src/main/java/com/example/ui/SfaApp.kt", "w") as f:
    f.writelines(lines)
