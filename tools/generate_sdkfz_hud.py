from pathlib import Path
import math
import re

import cairosvg


OUT = Path(__file__).resolve().parents[1] / (
    "src/main/resources/spark_modules/Machine-Max_Sdkfz234/machine_max/documents/sdkfz_hud"
)

DEFS = '''<defs>
  <linearGradient id="paint" x2="1" y2="1"><stop stop-color="#858174"/><stop offset=".45" stop-color="#66685e"/><stop offset="1" stop-color="#55594f"/></linearGradient>
  <linearGradient id="bakelite" x2=".2" y2="1"><stop stop-color="#6f5640"/><stop offset=".5" stop-color="#3d2e22"/><stop offset="1" stop-color="#756044"/></linearGradient>
  <linearGradient id="rim" x2=".8" y2="1"><stop stop-color="#c0b18b"/><stop offset=".3" stop-color="#696451"/><stop offset=".6" stop-color="#9c9175"/><stop offset="1" stop-color="#4b4d3f"/></linearGradient>
  <radialGradient id="face" cx=".3" cy=".2"><stop stop-color="#414034"/><stop offset="1" stop-color="#23281f"/></radialGradient>
  <linearGradient id="paper" x2=".15" y2="1"><stop stop-color="#c9ba91"/><stop offset=".45" stop-color="#dfd0a8"/><stop offset="1" stop-color="#d6c59a"/></linearGradient>
</defs>'''


def save(name, width, height, content):
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" '
           f'viewBox="0 0 {width} {height}">{DEFS}{content}</svg>')
    (OUT / f"{name}.svg").write_text(svg, encoding="utf-8")
    cairosvg.svg2png(bytestring=svg.encode(), write_to=str(OUT / f"{name}.png"), scale=2)


def screw(x, y):
    return (f'<circle cx="{x}" cy="{y}" r="2" fill="#aaa084"/>'
            f'<path d="M{x-1},{y+1}l2,-2" stroke="#4a4030" stroke-width="1"/>')


OUT.mkdir(parents=True, exist_ok=True)
driver = '''<rect x="85" y="23" width="146" height="66" rx="5" fill="url(#paint)" stroke="#474b40"/>
<path d="M105 25h119" stroke="#c0b590" opacity=".4"/>
<circle cx="53" cy="53" r="52" fill="#343b30"/>
<circle cx="53" cy="53" r="50" fill="url(#rim)"/>
<circle cx="53" cy="53" r="44" fill="url(#face)" stroke="#181f15" stroke-width="2"/>'''
for speed in range(0, 101, 5):
    angle = math.radians(-135 + speed * 2.7)
    outer = (53 + math.sin(angle) * 39, 53 - math.cos(angle) * 39)
    inner_radius = 33 if speed % 20 == 0 else 36
    inner = (53 + math.sin(angle) * inner_radius, 53 - math.cos(angle) * inner_radius)
    driver += f'<path d="M{inner[0]:.2f},{inner[1]:.2f}L{outer[0]:.2f},{outer[1]:.2f}" stroke="#d2c7a5" stroke-width="1"/>'
driver += '''<text x="53" y="39" text-anchor="middle" fill="#c3bc9e" font-family="serif" font-size="13">km/h</text>
<rect x="25" y="67" width="56" height="28" rx="2" fill="#21271c"/>
<path d="M28 68h50" stroke="#817c61" opacity=".35"/>'''
driver += screw(226, 29) + screw(226, 83)
save("driver-face", 232, 106, driver)
save("needle", 4, 42, '<path d="M2 0L3.7 38H.3Z" fill="#ddcda2"/>')

main = '''<rect x=".5" y=".5" width="367" height="123" rx="4" fill="url(#paint)" stroke="#3d4236"/>
<path d="M5 2h358" stroke="#c1b58f" opacity=".35"/>
<rect x="16" y="58" width="336" height="38" rx="3" fill="url(#bakelite)"/>
<rect x="20" y="60" width="328" height="33" rx="1" fill="url(#paper)"/>
<path d="M21 61h326v7L21 76Z" fill="#fff3c9" opacity=".12"/>'''
for percent in range(0, 101, 5):
    x = 24 + percent * 3.18
    y = 80 if percent % 25 == 0 else 85
    main += f'<path d="M{x:.2f},{y}v{91-y}" stroke="#715936" stroke-width=".8"/>'
for x, label, anchor in [(24, "0", "start"), (183, "50", "middle"), (342, "100", "end")]:
    main += f'<text x="{x}" y="75" text-anchor="{anchor}" font-family="serif" font-size="14" fill="#6a5234">{label}</text>'
main += screw(6, 7) + screw(362, 7)
save("main-face", 368, 124, main)
mg = '''<rect x=".5" y=".5" width="367" height="55" rx="2" fill="#4c5146" stroke="#3d4336"/>
<path d="M3 1h362" stroke="#a69b7d" opacity=".5"/>
<path d="M151 38h84" stroke="#776f58" stroke-width="1"/>'''
save("mg-face", 368, 56, mg)

speed = "math.min(100, math.abs(global.get('vehicle_speed') ?? 0) * 3.6)"


def angle_choice(low, high):
    if low == high:
        return f"'speed{low:03d}'"
    middle = (low + high) // 2
    return f"({speed} < {middle * 2 + 1} ? {angle_choice(low, middle)} : {angle_choice(middle + 1, high)})"


page = OUT / "driver.html"
html = page.read_text(encoding="utf-8")
html, count = re.subn(r'(<img class="speed-needle"[^>]*data-mm-class=")[^"]*(")',
                      lambda match: match[1] + angle_choice(0, 50) + match[2], html)
if count != 1:
    raise ValueError("driver.html must contain exactly one speed-needle binding")
page.write_text(html, encoding="utf-8", newline="\n")
(OUT / "needle-angles.css").write_text("\n".join(
    f".speed-needle.is-speed{i:03d} {{ transform: rotate({-135 + i * 5.4:.1f}deg); }}"
    for i in range(51)
) + "\n", encoding="utf-8", newline="\n")
print(f"Generated SdKfz HUD assets in {OUT}")


def pointer_choice(low, high):
    if low == high:
        return f"'p{low:03d}'"
    middle = (low + high) // 2
    return f"(weapon.tube_fill(0) < {middle + .5} ? {pointer_choice(low, middle)} : {pointer_choice(middle + 1, high)})"


for filename, classname in [("main_gun.html", "tuner-pointer"), ("mg34.html", "mg-pointer")]:
    page = OUT / filename
    html, count = re.subn(r'(<i class="' + classname + r'" data-mm-class=")[^"]*(")',
                          lambda match: match[1] + pointer_choice(0, 100) + match[2], page.read_text(encoding="utf-8"))
    if count != 1:
        raise ValueError(f"{filename} must contain exactly one {classname} binding")
    page.write_text(html, encoding="utf-8", newline="\n")
(OUT / "pointer-positions.css").write_text("\n".join(
    f".tuner-pointer.is-p{i:03d} {{ left: {i * 3.18:.2f}px; }}\n.mg-pointer.is-p{i:03d} {{ left: {i * .84:.2f}px; }}"
    for i in range(101)
) + "\n", encoding="utf-8", newline="\n")
