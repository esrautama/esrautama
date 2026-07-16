import sys

with open("app/src/main/java/com/example/ui/SfaApp.kt", "r") as f:
    text = f.read()

text = text.replace("@Composable\nfun AddEditDataDialog", "@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)\n@Composable\nfun AddEditDataDialog")

with open("app/src/main/java/com/example/ui/SfaApp.kt", "w") as f:
    f.write(text)
