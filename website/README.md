# Edit:RiN website

A single-screen, interactive introduction to Edit:RiN. The geometric letters
open the editor, live-parameter, and export chapters. The site is static HTML,
CSS, and JavaScript, with no build step or external runtime dependencies.

Official site: [rin-code-dev.github.io/Edit-RiN](https://rin-code-dev.github.io/Edit-RiN/).

## Local preview

From the repository root:

```sh
python3 -m http.server 8767 --bind 127.0.0.1 --directory website
```

Open `http://127.0.0.1:8767/`.

## Publishing

The [Pages workflow](../.github/workflows/pages.yml) deploys this directory to
GitHub Pages when its files change on `main`. It can also be run manually from
the repository's Actions tab. GitHub Pages must use **GitHub Actions** as its
publishing source. The repository root and Android build output are excluded
from the deployed site.

## Interaction

- Choose **Write**, **Tune**, or **Keep** by touching a letter.
- Drag **i** vertically, or use the labelled slider in **Tune**.
- Use **Back**, the Edit:RiN wordmark, or **Escape** to return.
- Pause the artwork and recordings with **Motion on/off**.
- **Save this artwork** creates a PNG of the site's letter composition.
- All navigation uses native buttons. The slider supports keyboard input.
  Reduced-motion preferences pause ambient animation and automatic video playback.

## Media and type

The short, silent MP4 excerpts show code with preview, live parameter changes,
and a touch-responsive sound sketch. Device status/navigation bars and audio
are excluded. Recordings load only when their chapter is opened.

The RiN lettering is drawn as original SVG geometry. Interface and display
text use [Space Grotesk](https://github.com/floriankarsten/space-grotesk);
code and small technical labels use [IBM Plex Mono](https://github.com/IBM/plex).
Font files and their SIL Open Font License texts are included in
`assets/fonts/`.

`share/` is a retirement notice for the old Web Player. It does not execute
code from URL parameters.
