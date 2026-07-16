const fs = require("fs");
const code = fs.readFileSync("prefix.js", "utf8");
const lines = code.split("\n");

let balance = 0;
for (let i = 0; i < lines.length; i++) {
  const line = lines[i];
  let lineBraces = 0;
  for (let col = 0; col < line.length; col++) {
    const char = line[col];
    if (char === "{") {
      balance++;
      lineBraces++;
    } else if (char === "}") {
      balance--;
      lineBraces--;
      if (balance < 0) {
        console.log(`WARNING: Balance became negative (${balance}) at line ${i + 1}, col ${col + 1}`);
      }
    }
  }
}
console.log(`Final balance of prefix.js: ${balance}`);
