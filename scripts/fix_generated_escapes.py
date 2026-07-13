from pathlib import Path

path = Path("MiruroApp/src/main/kotlin/com/ttvralph/miruroapp/data/MiruroRepository.kt")
lines = path.read_text().splitlines()
fixed = []
for line in lines:
    indent = line[: len(line) - len(line.lstrip())]
    if '?.replace(Regex("(?i)<br' in line:
        fixed.append(indent + '?.replace(Regex("""(?i)<br\\s*/?>"""), "\\n")')
    elif '?.replace("&quot;"' in line:
        fixed.append(indent + '?.replace("&quot;", 34.toChar().toString())')
    else:
        fixed.append(line)
path.write_text("\n".join(fixed) + "\n")
