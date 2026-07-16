const fs = require("fs");
const code = fs.readFileSync("prefix.js", "utf8");

let balance = 0;
let inString = null; // "or" or `
let inComment = null; // // or /*
let inRegex = false;

let i = 0;
while (i < code.length) {
  const char = code[i];
  const nextChar = code[i + 1];

  // Handle comments
  if (inComment === "//") {
    if (char === "\n") {
      inComment = null;
    }
    i++;
    continue;
  }
  if (inComment === "/*") {
    if (char === "*" && nextChar === "/") {
      inComment = null;
      i += 2;
    } else {
      i++;
    }
    continue;
  }

  // Handle strings
  if (inString) {
    if (char === "\\") {
      i += 2; // skip escape char
      continue;
    }
    if (char === inString) {
      inString = null;
    }
    i++;
    continue;
  }

  // Check for start of comments
  if (char === "/" && nextChar === "/") {
    inComment = "//";
    i += 2;
    continue;
  }
  if (char === "/" && nextChar === "*") {
    inComment = "/*";
    i += 2;
    continue;
  }

  // Check for start of strings
  if (char === "\"" || char === "\'" || char === "\`") {
    inString = char;
    i++;
    continue;
  }

  // Check for braces
  if (char === "{") {
    balance++;
  } else if (char === "}") {
    balance--;
    if (balance < 0) {
      console.log(`WARNING: Semantic balance became negative (${balance}) at position ${i}`);
    }
  }
  i++;
}

console.log(`True semantic balance of prefix.js: ${balance}`);
