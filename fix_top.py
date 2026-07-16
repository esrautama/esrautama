import sys

with open("app/src/main/java/com/example/ui/SfaApp.kt", "r") as f:
    lines = f.readlines()

while "package com.example.ui" not in lines[0]:
    lines.pop(0)

lines.insert(1, "import androidx.compose.material.icons.filled.HealthAndSafety\n")

with open("app/src/main/java/com/example/ui/SfaApp.kt", "w") as f:
    f.writelines(lines)
