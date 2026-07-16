import sys

with open("app/src/main/java/com/example/ui/SfaApp.kt", "r") as f:
    text = f.read()

# Fix double @Composable
text = text.replace("@Composable\n@Composable\nfun ReceiptPreviewDialog", "@Composable\nfun ReceiptPreviewDialog")
text = text.replace("@Composable\n@Composable\nfun AdminReceiptSettingsSection", "@Composable\nfun AdminReceiptSettingsSection")

# Add missing @Composable
text = text.replace("fun StockTab(", "@Composable\nfun StockTab(")
text = text.replace("fun AddEditDataDialog(", "@Composable\nfun AddEditDataDialog(")

with open("app/src/main/java/com/example/ui/SfaApp.kt", "w") as f:
    f.write(text)
